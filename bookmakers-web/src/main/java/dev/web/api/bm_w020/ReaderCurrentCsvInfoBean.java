package dev.web.api.bm_w020;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.getinfo.GetStatInfo;

@Component
public class ReaderCurrentCsvInfoBean {

    @Autowired
    private GetStatInfo getStatInfo;

    private Map<String, List<Integer>> csvInfo = Collections.emptyMap();

    public void init() {
        this.csvInfo = getStatInfo.getCsvInfo("0", null);
        if (this.csvInfo == null) this.csvInfo = Collections.emptyMap();
    }

    public Map<String, List<Integer>> getCsvInfo() {
        return csvInfo;
    }
}
