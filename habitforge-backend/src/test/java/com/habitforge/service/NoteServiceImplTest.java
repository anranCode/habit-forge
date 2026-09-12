package com.habitforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.study.dto.NoteImageResponse;
import com.habitforge.modules.study.entity.Note;
import com.habitforge.modules.study.entity.NoteImage;
import com.habitforge.modules.study.mapper.ChapterMapper;
import com.habitforge.modules.study.mapper.NoteImageMapper;
import com.habitforge.modules.study.mapper.NoteMapper;
import com.habitforge.modules.study.mapper.SubjectMapper;
import com.habitforge.modules.study.service.impl.NoteServiceImpl;
import com.habitforge.modules.storage.service.StorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 笔记服务测试(图片上传流程含落库失败回收/摘要截断/归属校验)
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock
    private NoteMapper noteMapper;
    @Mock
    private NoteImageMapper noteImageMapper;
    @Mock
    private SubjectMapper subjectMapper;
    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private StorageService storageService;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private NoteServiceImpl noteService;

    private static final String USER_ID = "user-a";
    private static final String NOTE_ID = "note-1";

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Note.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), NoteImage.class);
    }

    private Note note(String userId) {
        Note n = new Note();
        n.setId(NOTE_ID);
        n.setUserId(userId);
        n.setSubjectId("subject-1");
        n.setTitle("标题");
        return n;
    }

    private MockMultipartFile pngFile() {
        // 非真实 PNG 字节: ImageIO 读不出宽高 → null, 与 journal webp 兜底路径一致
        return new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
    }

    // ================= 摘要截断 =================

    @Test
    void excerpt_stripsMarkdownAndTruncates100() {
        assertEquals("", NoteServiceImpl.excerpt(null));
        assertEquals("", NoteServiceImpl.excerpt("   "));

        String plain = NoteServiceImpl.excerpt("# 一级标题\n\n**加粗** 与 `代码`\n\n- 列表项");
        assertEquals("一级标题 加粗 与 代码 列表项", plain);

        // 链接保留文字
        assertEquals("详见 官方文档", NoteServiceImpl.excerpt("详见 [官方文档](https://example.com)"));

        // 100 字截断
        String longText = "记".repeat(150);
        String cut = NoteServiceImpl.excerpt(longText);
        assertEquals(100, cut.length());
        assertTrue(cut.startsWith("记记记"));
    }

    // ================= 归属校验 =================

    @Test
    void getDetail_crossUser_returns7006() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note("user-b"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.getDetail(USER_ID, NOTE_ID));
        assertEquals(7006, e.getCode());
    }

    // ================= 图片上传(照搬 journal 流程) =================

    @Test
    void uploadImage_success_putsMinioThenPersists() {
        when(redisUtil.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(USER_ID));
        when(noteImageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(noteImageMapper.insert(any(NoteImage.class))).thenReturn(1);

        NoteImageResponse resp = noteService.uploadImage(USER_ID, NOTE_ID, pngFile());

        // objectKey = habitforge/note/{yyyy}/{MM}/{uuid}.png
        String expectPrefix = "habitforge/note/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/";
        assertTrue(resp.getObjectKey().startsWith(expectPrefix), resp.getObjectKey());
        assertTrue(resp.getObjectKey().endsWith(".png"));
        assertEquals("a.png", resp.getOriginalName());
        assertEquals("image/png", resp.getContentType());
        assertEquals(3, resp.getFileSize());
        assertEquals(0, resp.getSortOrder());
        // 非真实图片字节: ImageIO 读不出宽高 → null(与 journal 一致)
        assertNull(resp.getWidth());
        assertNull(resp.getHeight());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).putObject(keyCaptor.capture(), any(), any(Long.class), anyString());
        assertEquals(resp.getObjectKey(), keyCaptor.getValue());
        verify(storageService, never()).deleteObjectQuietly(anyString());
    }

    @Test
    void uploadImage_dbInsertFailure_recyclesObject() {
        when(redisUtil.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(USER_ID));
        when(noteImageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(noteImageMapper.insert(any(NoteImage.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> noteService.uploadImage(USER_ID, NOTE_ID, pngFile()));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).putObject(keyCaptor.capture(), any(), any(Long.class), anyString());
        // 先传后落库失败 → 回收 MinIO 对象
        verify(storageService).deleteObjectQuietly(keyCaptor.getValue());
    }

    @Test
    void uploadImage_invalidType_returns4005() {
        when(redisUtil.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(USER_ID));

        MockMultipartFile exe = new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1});
        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.uploadImage(USER_ID, NOTE_ID, exe));
        assertEquals(4005, e.getCode());
        verify(storageService, never()).putObject(anyString(), any(), any(Long.class), anyString());
    }

    @Test
    void uploadImage_rateLimited_returns429() {
        when(redisUtil.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.uploadImage(USER_ID, NOTE_ID, pngFile()));
        assertEquals(429, e.getCode());
    }

    @Test
    void uploadImage_foreignNote_returns7006() {
        when(redisUtil.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note("user-b"));
        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.uploadImage(USER_ID, NOTE_ID, pngFile()));
        assertEquals(7006, e.getCode());
    }

    // ================= 图片删除 / 笔记删除清 MinIO =================

    @Test
    void deleteImage_removesRowAndObject() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(USER_ID));
        NoteImage image = new NoteImage();
        image.setId("img-1");
        image.setNoteId(NOTE_ID);
        image.setObjectKey("habitforge/note/2026/09/x.png");
        when(noteImageMapper.selectById("img-1")).thenReturn(image);

        noteService.deleteImage(USER_ID, NOTE_ID, "img-1");

        verify(noteImageMapper).deleteById("img-1");
        verify(storageService).deleteObjectQuietly("habitforge/note/2026/09/x.png");
    }

    @Test
    void delete_collectsKeysThenCleansQuietly() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(USER_ID));
        NoteImage image = new NoteImage();
        image.setNoteId(NOTE_ID);
        image.setObjectKey("habitforge/note/2026/09/y.png");
        when(noteImageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of(image));

        noteService.delete(USER_ID, NOTE_ID);

        verify(noteMapper).deleteById(NOTE_ID);
        verify(storageService).deleteObjectQuietly("habitforge/note/2026/09/y.png");
    }
}
