package com.example.knavi.ingestion.application;

import com.example.knavi.audit.AuditLog;
import com.example.knavi.shared.security.AccessPolicy;
import com.example.knavi.shared.security.AuthenticatedUser;
import com.example.knavi.shared.web.ApiErrors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原本ファイル（PDF）のアップロード。検証してから S3 互換ストレージに置き、stored_files に記録する（ADR-0010）。
 *
 * <ul>
 *   <li>PDF だけを受け付ける（拡張子や Content-Type ではなく、先頭の {@code %PDF-} で確かめる）
 *   <li>同じ内容（SHA-256 が同じ）のファイルは保存し直さず、既存のものを返す
 *   <li>元のファイル名は表示用にだけ使い、保存キーには使わない
 * </ul>
 */
@Service
public class FileUploadService {

    static final String PDF = "application/pdf";
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final JdbcClient jdbc;
    private final FileStorage storage;
    private final AccessPolicy accessPolicy;
    private final AuditLog auditLog;
    private final long maxBytes;

    FileUploadService(
            JdbcClient jdbc,
            FileStorage storage,
            AccessPolicy accessPolicy,
            AuditLog auditLog,
            @Value("${knavi.storage.max-upload-bytes}") long maxBytes) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.accessPolicy = accessPolicy;
        this.auditLog = auditLog;
        this.maxBytes = maxBytes;
    }

    /**
     * @param duplicate true なら同じ内容のファイルが既にあり、それを返した
     */
    public record StoredFileView(
            UUID id,
            String originalName,
            String mimeType,
            long sizeBytes,
            String sha256,
            OffsetDateTime createdAt,
            boolean duplicate) {}

    @Transactional
    public StoredFileView upload(String originalName, byte[] content, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        StoredFileView stored = store(originalName, content, user.id());
        if (!stored.duplicate()) {
            auditLog.record(
                    user.id(),
                    "FILE_UPLOADED",
                    "FILE",
                    stored.id(),
                    Map.of("sha256", stored.sha256(), "sizeBytes", content.length));
        }
        return stored;
    }

    /** データ投入（システム処理）から使う。アップロードした人は記録しない */
    @Transactional
    UUID storeForSystem(String originalName, byte[] content) {
        return store(originalName, content, null).id();
    }

    private StoredFileView store(String originalName, byte[] content, UUID uploadedBy) {
        if (content.length == 0) {
            throw ApiErrors.badRequest("ファイルが空です。");
        }
        if (content.length > maxBytes) {
            throw ApiErrors.badRequest("ファイルが大きすぎます（上限 " + maxBytes / 1024 / 1024 + "MB）。");
        }
        if (content.length < PDF_MAGIC.length
                || !Arrays.equals(content, 0, PDF_MAGIC.length, PDF_MAGIC, 0, PDF_MAGIC.length)) {
            throw ApiErrors.badRequest("PDF ファイルだけをアップロードできます。");
        }
        String sha256 = sha256(content);
        var existing = find("sha256 = :v", sha256);
        if (existing != null) {
            return withDuplicate(existing);
        }

        UUID id = UUID.randomUUID();
        // 保存キーは内容と ID から作る（利用者が付けたファイル名はパスに使わない）
        String key = "files/" + sha256.substring(0, 2) + "/" + id + ".pdf";
        storage.put(key, content, PDF);
        jdbc.sql("""
                        INSERT INTO stored_files (id, storage_key, original_name, mime_type, size_bytes, sha256, uploaded_by)
                        VALUES (:id, :key, :name, :mime, :size, :sha256, :user)
                        """)
                .param("id", id)
                .param("key", key)
                .param("name", displayName(originalName))
                .param("mime", PDF)
                .param("size", (long) content.length)
                .param("sha256", sha256)
                .param("user", uploadedBy)
                .update();
        return find("id = :v", id);
    }

    @Transactional(readOnly = true)
    public StoredFileView get(UUID id, AuthenticatedUser user) {
        accessPolicy.checkIsContentEditor(user);
        var file = find("id = :v", id);
        if (file == null) {
            throw ApiErrors.notFound("ファイルが見つかりません。");
        }
        return file;
    }

    /** 原本の中身。ストレージに無ければ 404 */
    @Transactional(readOnly = true)
    public byte[] content(UUID id, AuthenticatedUser user) {
        get(id, user);
        String key = jdbc.sql("SELECT storage_key FROM stored_files WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .single();
        return storage.get(key).orElseThrow(() -> ApiErrors.notFound("ファイルの中身が見つかりません。"));
    }

    /** 取り込みジョブ（システム処理）から使う。権限は受付時に確かめている */
    byte[] contentForJob(UUID id) {
        String key = jdbc.sql("SELECT storage_key FROM stored_files WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .optional()
                .orElseThrow(() -> ApiErrors.notFound("ファイルが見つかりません。"));
        return storage.get(key).orElseThrow(() -> ApiErrors.notFound("ファイルの中身が見つかりません。"));
    }

    private StoredFileView find(String condition, Object value) {
        return jdbc.sql("SELECT id, original_name, mime_type, size_bytes, sha256, created_at FROM stored_files WHERE "
                        + condition)
                .param("v", value)
                .query((rs, i) -> new StoredFileView(
                        rs.getObject("id", UUID.class),
                        rs.getString("original_name"),
                        rs.getString("mime_type"),
                        rs.getLong("size_bytes"),
                        rs.getString("sha256"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        false))
                .optional()
                .orElse(null);
    }

    private static StoredFileView withDuplicate(StoredFileView f) {
        return new StoredFileView(
                f.id(), f.originalName(), f.mimeType(), f.sizeBytes(), f.sha256(), f.createdAt(), true);
    }

    /** 表示用のファイル名。フォルダ名と制御文字を除き、長さを抑える */
    static String displayName(String originalName) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            name = "untitled.pdf";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
