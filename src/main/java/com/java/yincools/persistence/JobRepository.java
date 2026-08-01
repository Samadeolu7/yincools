package com.java.yincools.persistence;

import com.java.yincools.domain.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findTop20ByOrderByIdDesc();
}
