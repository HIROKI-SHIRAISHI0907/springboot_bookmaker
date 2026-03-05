package dev.batch.bm_b010;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.MatchKeySaveRepository;

@Service
public class FinGettingTruncate {

	@Autowired
	MatchKeySaveRepository repo;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void truncate() {
		repo.truncate();
	}

}
