package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.Poll;
import com.infiniteplay.accord.entities.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoteRepository extends CrudRepository<Vote, Integer> {

    @Query("DELETE FROM Vote v WHERE v.id in :ids")
    @Modifying
    void bulkDeleteByIds(@Param("ids") List<Integer> ids);
}