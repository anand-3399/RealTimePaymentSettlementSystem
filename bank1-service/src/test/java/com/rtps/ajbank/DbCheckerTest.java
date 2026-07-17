package com.rtps.bank1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class DbCheckerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void dumpAccountingEntries() {
        System.out.println("====== DB DUMP START ======");
        List<Map<String, Object>> entries = jdbcTemplate.queryForList("SELECT ENTRY_ID, ENTRY_STATUS, COMMITTED_AT FROM ACCOUNTING_ENTRIES");
        for (Map<String, Object> entry : entries) {
            System.out.println("ENTRY: " + entry.get("ENTRY_ID") + " | STATUS: " + entry.get("ENTRY_STATUS") + " | COMMITTED_AT: " + entry.get("COMMITTED_AT"));
        }
        System.out.println("====== DB DUMP END ======");
    }
}
