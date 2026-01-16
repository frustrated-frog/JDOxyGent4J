package com.jd.oxygent.studio.test.es;

import com.jd.oxygent.infra.databases.es.RemoteEs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Remote Elasticsearch Test
 * 
 * @author OxyGent Team
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@SpringBootApplication(scanBasePackages = {"com.jd.oxygent", "com.jd.oxygent.web"})
public class RemoteEsTest {

    @Autowired
    private RemoteEs remoteEs;

    private static final String TEST_INDEX = "test_remote_es";
    private static final String TEST_DOC_ID = "test_doc_1";
    private static final String MAPPING_DIR = "D:/workspace/JDOxyGent4J-github/cache_dir/local_es_data";

    @Before
    public void setUp() throws IOException {
        // Create index with mapping from cache_dir

        Map<String, Object> properties = new HashMap<>();
        properties.put("message_id", Map.of("type", "keyword"));
        properties.put("group_id", Map.of("type", "keyword"));
        properties.put("trace_id", Map.of("type", "keyword"));
        properties.put("node_id", Map.of("type", "keyword"));
        properties.put("node_name", Map.of("type", "keyword"));
        properties.put("message", Map.of("type", "text"));
        properties.put("message_type", Map.of("type", "keyword"));
        properties.put("message_event", Map.of("type", "keyword"));
        properties.put("message_timestamp", Map.of("type", "long"));
        properties.put("create_time", Map.of(
                "type", "date",
                "format", "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"
        ));
        properties.put("from_trace_id", Map.of("type", "keyword"));

        Map<String, Object> mappings = Map.of("properties", properties);
        // If you need the outer "mappings" wrapper
        Map<String, Object> root = Map.of("mappings", mappings);

        Map<String, Object> result = remoteEs.createIndex(TEST_INDEX, root);
        assertNotNull(result);
        assertTrue((Boolean) result.get("acknowledged"));
    }

    @After
    public void tearDown() {
        // Delete test index
        Map<String, Object> result = remoteEs.deleteIndex(TEST_INDEX);
        assertNotNull(result);
    }

    @Test
    public void testIndexDocument() {
        // Create test document with fields from properties map
        Map<String, Object> doc = Map.of(
                "message_id", "msg_123",
                "group_id", "group_456",
                "trace_id", "trace_789",
                "node_id", "node_101",
                "node_name", "test_node",
                "message", "Test message content",
                "message_type", "text",
                "message_event", "send",
                "message_timestamp", System.currentTimeMillis() + "",
                "create_time", "2026-01-15 10:30:45.123456789"
        );
        
        // Index document
        Map<String, Object> result = remoteEs.index(TEST_INDEX, TEST_DOC_ID, doc);
        assertNotNull(result);
        assertFalse(result.containsKey("error"));
        assertEquals(TEST_DOC_ID, result.get("_id"));
        
        // Refresh index to make document available
        remoteEs.refreshIndex(TEST_INDEX);
        
        // Check if document exists
        boolean exists = remoteEs.exists(TEST_INDEX, TEST_DOC_ID);
        assertTrue(exists);
    }

    @Test
    public void testUpdateDocument() {
        // First, create a document with fields from properties map
        Map<String, Object> doc = Map.of(
                "message_id", "msg_123",
                "group_id", "group_456",
                "trace_id", "trace_789",
                "node_id", "node_101",
                "node_name", "test_node",
                "message", "Test message content",
                "message_type", "text",
                "message_event", "send",
                "message_timestamp", System.currentTimeMillis() + "",
                "create_time", "2026-01-15 10:30:45.123456789"
        );
        
        remoteEs.index(TEST_INDEX, TEST_DOC_ID, doc);
        remoteEs.refreshIndex(TEST_INDEX);
        
        // Then update it with valid fields from properties map
        Map<String, Object> updateFields = Map.of(
            "message", "Updated message content",
            "message_event", "update",
            "message_timestamp", System.currentTimeMillis()
        );
        
        Map<String, Object> result = remoteEs.update(TEST_INDEX, TEST_DOC_ID, updateFields);
        assertNotNull(result);
        assertFalse(result.containsKey("error"));
        assertEquals(TEST_DOC_ID, result.get("_id"));
        
        remoteEs.refreshIndex(TEST_INDEX);
    }

    @Test
    public void testSearchDocuments() {
        // Create multiple test documents with fields from properties map
        for (int i = 0; i < 5; i++) {
            Map<String, Object> doc = Map.of(
                "message_id", "msg_" + i,
                "group_id", "group_" + (i % 2),
                "trace_id", "trace_" + i,
                "node_id", "node_" + i,
                "message", "Test message " + i,
                "message_type", i % 2 == 0 ? "text" : "system",
                "message_event", "send",
                "message_timestamp", System.currentTimeMillis() + i,
                "create_time", "2026-01-15 10:30:45.123456789",
                "from_trace_id", "from_trace_" + i
            );
            remoteEs.index(TEST_INDEX, "doc_" + i, doc);
        }
        
        remoteEs.refreshIndex(TEST_INDEX);
        
        // Test simple search by group_id
        Map<String, Object> match = Map.of("group_id", "group_0");
        Map<String, Object> query = Map.of("match", match);
        Map<String, Object> searchQuery = Map.of("query", query);
        
        Map<String, Object> result = remoteEs.search(TEST_INDEX, searchQuery);
        assertNotNull(result);
        assertNotNull(result.get("hits"));
        
        // Test search with sort by message_timestamp
        Map<String, Object> timestampSort = Map.of("order", "desc");
        Map<String, Object> sort = Map.of("message_timestamp", timestampSort);
        Map<String, Object> sortedSearch = Map.of(
            "query", query,
            "sort", new Object[]{sort}
        );
        
        result = remoteEs.search(TEST_INDEX, sortedSearch);
        assertNotNull(result);
        assertNotNull(result.get("hits"));
    }

    @Test
    public void testDeleteDocument() {
        // Create a test document first with fields from properties map
        Map<String, Object> doc = Map.of(
                "message_id", "msg_to_delete",
                "group_id", "group_delete",
                "trace_id", "trace_delete",
                "node_id", "node_delete",
                "message", "Document to be deleted",
                "message_type", "text",
                "message_event", "send",
                "message_timestamp", System.currentTimeMillis(),
                "create_time", "2026-01-15 10:30:45.123456789",
                "from_trace_id", "from_trace_delete"
        );
        
        remoteEs.index(TEST_INDEX, "doc_to_delete", doc);
        remoteEs.refreshIndex(TEST_INDEX);
        
        // Verify it exists
        assertTrue(remoteEs.exists(TEST_INDEX, "doc_to_delete"));
        
        // Delete the document
        Map<String, Object> result = remoteEs.delete(TEST_INDEX, "doc_to_delete");
        assertNotNull(result);
        assertFalse(result.containsKey("error"));
        assertEquals("doc_to_delete", result.get("_id"));
        
        remoteEs.refreshIndex(TEST_INDEX);
        
        // Verify it's deleted
        assertFalse(remoteEs.exists(TEST_INDEX, "doc_to_delete"));
    }

    @Test
    public void testComplexSearch() {
        // Create test documents with fields from properties map
        for (int i = 0; i < 10; i++) {
            Map<String, Object> doc = Map.of(
                "message_id", "complex_msg_" + i,
                "group_id", "group_complex_" + (i % 3),
                "trace_id", "trace_complex_" + i,
                "node_id", "node_complex_" + i,
                "message", "Complex test message " + i + " with keyword",
                "message_type", i < 5 ? "text" : "system",
                "message_event", "send",
                "message_timestamp", System.currentTimeMillis() + i * 1000,
                "create_time", "2026-01-15 10:30:45.123456789",
                "from_trace_id", "from_trace_complex_" + i
            );
            remoteEs.index(TEST_INDEX, "complex_doc_" + i, doc);
        }
        
        remoteEs.refreshIndex(TEST_INDEX);
        
        // Test bool query with must, should, and filter
        // Must clauses
        // Bool query with inline intermediate maps
        Map<String, Object> boolQuery = Map.of(
                "must", new Object[]{Map.of(
                        "match", Map.of("message", "keyword")
                )},
                "should", new Object[]{
                        Map.of("match", Map.of("group_id", "group_complex_0")),
                        Map.of("match", Map.of("group_id", "group_complex_1"))
                },
                "filter", new Object[]{Map.of(
                        "term", Map.of("message_type", "text")
                )}
        );
        // Complex query
        Map<String, Object> complexQuery = Map.of(
                "query", Map.of("bool", boolQuery),
                "size", 10,
                "_source", List.of("trace_id", "group_id", "create_time"),
                "sort", List.of(Map.of("create_time", Map.of("order", "desc")))
        );

        Map<String, Object> result = remoteEs.search(TEST_INDEX, complexQuery);
        assertNotNull(result);
        assertNotNull(result.get("hits"));
    }
}