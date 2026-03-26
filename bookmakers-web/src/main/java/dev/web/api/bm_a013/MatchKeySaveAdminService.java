package dev.web.api.bm_a013;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.MatchKeyRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchKeySaveAdminService {

    private final MatchKeyRepository matchKeyRepository;

    public List<String> getAllMatchKeys() {
        return matchKeyRepository.findAllMatchKeys();
    }

}
