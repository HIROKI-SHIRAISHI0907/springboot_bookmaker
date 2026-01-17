package dev.web.api.bm_w013;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.master.TeamMemberMasterWebRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamMemberFindAllService {

    private final TeamMemberMasterWebRepository repo;

    @Transactional(readOnly = true)
    public List<TeamMemberDTO> getAll() {
        return repo.findAll();
    }
}
