package com.ruoyi.rag.mapper;

import com.ruoyi.rag.entity.RagChunk;
import com.ruoyi.rag.entity.RagDocument;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RagDocumentMapper {

    @Insert("INSERT INTO rag_document(library_id,file_name,file_type,file_size,doc_type,status,create_by) " +
            "VALUES(#{libraryId},#{fileName},#{fileType},#{fileSize},#{docType},'0',#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDocument(RagDocument doc);

    @Update("UPDATE rag_document SET status='1',chunk_count=#{chunkCount},update_time=NOW() WHERE id=#{id}")
    int markDone(@Param("id") Long id, @Param("chunkCount") int chunkCount);

    @Select("SELECT * FROM rag_chunk WHERE embedding IS NOT NULL")
    List<RagChunk> getAllChunks();

    @Select("SELECT c.* FROM rag_chunk c JOIN rag_document d ON c.document_id=d.id WHERE d.library_id=#{libraryId}")
    List<RagChunk> getChunksByLibrary(Long libraryId);

    @Insert("INSERT INTO rag_chunk(document_id,chunk_index,content,embedding,token_count) " +
            "VALUES(#{documentId},#{chunkIndex},#{content},#{embedding},#{tokenCount})")
    int insertChunk(RagChunk chunk);

    @Update("UPDATE rag_chunk SET content=#{content},embedding=#{embedding},token_count=#{tokenCount} WHERE id=#{id}")
    int updateChunk(RagChunk chunk);

    @Select("SELECT * FROM rag_chunk WHERE id=#{id}")
    RagChunk getChunkById(Long id);

    @Delete("DELETE FROM rag_chunk WHERE id=#{id}")
    int deleteChunkById(Long id);

    @Update("UPDATE rag_document SET chunk_count = (SELECT COUNT(*) FROM rag_chunk WHERE document_id=#{docId}) WHERE id=#{docId}")
    int refreshChunkCount(Long docId);

    @Select("SELECT * FROM rag_document WHERE library_id=#{libraryId} ORDER BY create_time DESC")
    List<RagDocument> listByLibrary(Long libraryId);

    @Select("SELECT * FROM rag_document ORDER BY create_time DESC")
    List<RagDocument> listAll();

    @Select("SELECT * FROM rag_document WHERE id=#{id}")
    RagDocument getById(Long id);

    @Delete("DELETE FROM rag_document WHERE id=#{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM rag_document WHERE library_id=#{libraryId}")
    int deleteByLibrary(Long libraryId);

    @Select("SELECT * FROM rag_chunk WHERE document_id=#{documentId} ORDER BY chunk_index")
    List<RagChunk> getChunksByDocId(Long documentId);

    @Delete("DELETE FROM rag_chunk WHERE document_id=#{documentId}")
    int deleteChunksByDocId(Long documentId);

    @Delete("DELETE c FROM rag_chunk c JOIN rag_document d ON c.document_id=d.id WHERE d.library_id=#{libraryId}")
    int deleteChunksByLibrary(Long libraryId);

    /**
     * 关键词检索：匹配分块内容
     */
    @Select("SELECT c.* FROM rag_chunk c WHERE c.content LIKE CONCAT('%',#{keyword},'%') ORDER BY c.create_time DESC LIMIT #{limit}")
    List<RagChunk> search(@Param("keyword") String keyword, @Param("limit") int limit);
}
