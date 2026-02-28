package dev.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.web.api.bm_w013.StatResponseResource;
import dev.web.api.bm_w015.FinGettingRequest;
import dev.web.batch.EcsBatchTaskRunner;
import lombok.RequiredArgsConstructor;

/**
 * FinGettingJsonタスク実行用
 * @author shiraishitoshio
 *
 */
@RestController
@RequestMapping("/api/admin/exec/task")
@RequiredArgsConstructor
public class FinGettingExecTaskJsonController {

    private final EcsBatchTaskRunner runner;

    /**
     * /fin-getting-json を叩いたら B008 のFargateタスクを起動する
     */
    @PostMapping("/fin-getting-json")
    public ResponseEntity<StatResponseResource> execute(
    		@RequestBody FinGettingRequest req) {

    	Map<String, String> env = new HashMap<>();

        List<FinGettingRequest.Item> items = req.getMatches();
        env.put("FIN_GETTING_COUNT", String.valueOf(items.size()));

        for (int i = 0; i < items.size(); i++) {
            FinGettingRequest.Item it = items.get(i);

            env.put("FIN_GETTING_" + i + "_DATE", it.getMatchDate().toString());
            env.put("FIN_GETTING_" + i + "_MATCH_ID", it.getMatchId());

            // matchUrl は任意：空なら env に入れない方が綺麗（どちらでもOK）
            if (it.getMatchUrl() != null && !it.getMatchUrl().isBlank()) {
                env.put("FIN_GETTING_" + i + "_MATCH_URL", it.getMatchUrl().trim());
            }
        }

        String taskArn = runner.runBatch("B008", env);

        StatResponseResource res = new StatResponseResource();
        // あなたのDTO設計に合わせて詰めてOK
        res.setReturnCd("ACCEPTED");
        // resに taskArn を入れられるなら入れるのがおすすめ（進捗追跡できる）
        res.setTaskArn(taskArn);

        return ResponseEntity.ok(res);
    }
}
