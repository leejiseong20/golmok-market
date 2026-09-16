package com.golmok.market.global;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 스키마(golmok_schema_v2.sql)의 UNIQUE 제약이 엔티티에도 선언돼 있는지 확인한다.
 *
 * 왜 필요한가: 테스트는 H2 를 엔티티 기준으로 만든다(ddl-auto: create-drop).
 * 엔티티에 UNIQUE 를 선언하지 않으면 제약이 테스트 DB 에만 빠져서,
 * "중복이 막히는지" 검증하는 테스트가 통과해 버리고 운영 MySQL 에서만 터진다.
 * 실제로 favorites 에서 이 일이 있었다.
 *
 * 컬럼·타입 일치는 운영에서 ddl-auto: validate 가 잡아주지만, UNIQUE 는 validate 대상이 아니다.
 */
@SpringBootTest
@Transactional
class SchemaConstraintTest {

    private static final Path SCHEMA = Path.of("golmok_schema_v2.sql");
    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE (\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIQUE_KEY = Pattern.compile("UNIQUE KEY \\w+ \\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager em;

    @Test
    void 스키마_SQL_의_UNIQUE_제약이_엔티티에도_모두_선언돼_있다() throws IOException {
        Map<String, Set<Set<String>>> expected = uniqueKeysInSchemaFile();
        Map<String, Set<Set<String>>> actual = uniqueConstraintsInTestDatabase();

        List<String> missing = new ArrayList<>();
        expected.forEach((table, keys) -> keys.stream()
                .filter(columns -> !actual.getOrDefault(table, Set.of()).contains(columns))
                .forEach(columns -> missing.add(table + columns)));

        assertThat(missing)
                .describedAs("""
                        엔티티에 @Table(uniqueConstraints = ...) 선언이 빠졌다.
                        스키마 SQL 에는 있지만 엔티티에 없으면 H2 테스트에서만 중복이 허용된다.""")
                .isEmpty();
    }

    /** 운영 스키마 파일에서 테이블별 UNIQUE 컬럼 조합을 뽑는다. */
    private Map<String, Set<Set<String>>> uniqueKeysInSchemaFile() throws IOException {
        Map<String, Set<Set<String>>> keys = new HashMap<>();
        String table = null;
        for (String line : Files.readAllLines(SCHEMA, StandardCharsets.UTF_8)) {
            Matcher createTable = CREATE_TABLE.matcher(line);
            if (createTable.find()) {
                table = createTable.group(1).toLowerCase();
                continue;
            }
            Matcher uniqueKey = UNIQUE_KEY.matcher(line);
            if (table != null && uniqueKey.find()) {
                keys.computeIfAbsent(table, ignored -> new HashSet<>()).add(columns(uniqueKey.group(1)));
            }
        }
        assertThat(keys).describedAs("스키마 파일을 읽지 못했거나 형식이 바뀌었다").isNotEmpty();
        return keys;
    }

    /** 엔티티로 만들어진 테스트 DB(H2)의 UNIQUE 제약을 읽는다. */
    private Map<String, Set<Set<String>>> uniqueConstraintsInTestDatabase() {
        em.flush(); // 스키마 생성이 끝난 뒤에 읽는다
        Map<String, Map<String, Set<String>>> byConstraint = new HashMap<>();
        jdbcTemplate.query("""
                select tc.table_name, tc.constraint_name, kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name
                 and kcu.table_name = tc.table_name
                where tc.constraint_type = 'UNIQUE'
                """, rs -> {
            String table = rs.getString("table_name").toLowerCase();
            String constraint = rs.getString("constraint_name");
            byConstraint.computeIfAbsent(table, ignored -> new HashMap<>())
                    .computeIfAbsent(constraint, ignored -> new TreeSet<>())
                    .add(rs.getString("column_name").toLowerCase());
        });

        Map<String, Set<Set<String>>> constraints = new HashMap<>();
        byConstraint.forEach((table, perConstraint) ->
                constraints.put(table, new HashSet<>(perConstraint.values())));
        return constraints;
    }

    /** "user_id, product_id" → 정렬된 컬럼 집합. 선언 순서 차이는 무시한다. */
    private static Set<String> columns(String columnList) {
        Set<String> columns = new TreeSet<>();
        for (String column : columnList.split(",")) {
            columns.add(column.trim().toLowerCase());
        }
        return columns;
    }
}
