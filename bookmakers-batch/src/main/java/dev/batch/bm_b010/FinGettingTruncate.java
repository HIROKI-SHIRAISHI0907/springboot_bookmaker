package dev.batch.bm_b010;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.batch.repository.bm.MatchKeySaveRepository;

@Service
public class FinGettingTruncate {

	@Autowired
	MatchKeySaveRepository repo;

	public void truncate() {
		repo.truncate();
	}

}
