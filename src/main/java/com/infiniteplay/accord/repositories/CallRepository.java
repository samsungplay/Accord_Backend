package com.infiniteplay.accord.repositories;

import com.infiniteplay.accord.entities.Call;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CallRepository extends JpaRepository<Call, Integer> {


}
