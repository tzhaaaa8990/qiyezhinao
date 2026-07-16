package com.ruoyi.rag.mapper;

import com.ruoyi.rag.entity.RagLibrary;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RagLibraryMapper {

    @Select("SELECT l.*, " +
            "(SELECT COUNT(*) FROM rag_document d WHERE d.library_id = l.id) AS doc_count, " +
            "(SELECT IFNULL(SUM(LENGTH(c.content)),0) FROM rag_chunk c JOIN rag_document d2 ON c.document_id = d2.id WHERE d2.library_id = l.id) AS char_count " +
            "FROM rag_library l ORDER BY l.create_time DESC")
    List<RagLibrary> listWithStats();

    @Select("SELECT * FROM rag_library WHERE id=#{id}")
    RagLibrary getById(Long id);

    @Insert("INSERT INTO rag_library(name,description,chunk_size,overlap_size,`separator`,top_k,score_threshold,create_by) " +
            "VALUES(#{name},#{description},#{chunkSize},#{overlapSize},#{separator},#{topK},#{scoreThreshold},#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RagLibrary library);

    @Update("UPDATE rag_library SET name=#{name},description=#{description},chunk_size=#{chunkSize}," +
            "overlap_size=#{overlapSize},`separator`=#{separator},top_k=#{topK},score_threshold=#{scoreThreshold} WHERE id=#{id}")
    int update(RagLibrary library);

    @Delete("DELETE FROM rag_library WHERE id=#{id}")
    int deleteById(Long id);
}
