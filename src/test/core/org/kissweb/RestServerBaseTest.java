package org.kissweb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RestServerBase} — content-type classification
 * (public static methods accessible from anywhere).
 */
class RestServerBaseTest {

    // ------------------------------------------------------------------
    // isTextContentType
    // ------------------------------------------------------------------

    @Test
    void testIsTextContentType_json() {
        assertTrue(RestServerBase.isTextContentType("application/json"));
    }

    @Test
    void testIsTextContentType_html() {
        assertTrue(RestServerBase.isTextContentType("text/html"));
    }

    @Test
    void testIsTextContentType_xml() {
        assertTrue(RestServerBase.isTextContentType("application/xml"));
    }

    @Test
    void testIsTextContentType_plain() {
        assertTrue(RestServerBase.isTextContentType("text/plain"));
    }

    @Test
    void testIsTextContentType_css() {
        assertTrue(RestServerBase.isTextContentType("text/css"));
    }

    @Test
    void testIsTextContentType_javascript() {
        assertTrue(RestServerBase.isTextContentType("application/javascript"));
    }

    @Test
    void testIsTextContentType_octetStream_false() {
        assertFalse(RestServerBase.isTextContentType("application/octet-stream"));
    }

    @Test
    void testIsTextContentType_imagePng_false() {
        assertFalse(RestServerBase.isTextContentType("image/png"));
    }

    // ------------------------------------------------------------------
    // isBinaryContentType
    // ------------------------------------------------------------------

    @Test
    void testIsBinaryContentType_octetStream() {
        assertTrue(RestServerBase.isBinaryContentType("application/octet-stream"));
    }

    @Test
    void testIsBinaryContentType_imagePng() {
        assertTrue(RestServerBase.isBinaryContentType("image/png"));
    }

    @Test
    void testIsBinaryContentType_json_false() {
        assertFalse(RestServerBase.isBinaryContentType("application/json"));
    }

    @Test
    void testIsBinaryContentType_html_false() {
        assertFalse(RestServerBase.isBinaryContentType("text/html"));
    }

    // ------------------------------------------------------------------
    // consistency: text and binary are complementary
    // ------------------------------------------------------------------

    @Test
    void testTextAndBinary_areComplementary() {
        String[] types = {
                "application/json", "text/html", "text/plain",
                "application/octet-stream", "image/png", "image/jpeg",
                "application/xml", "text/css"
        };
        for (String type : types) {
            assertEquals(!RestServerBase.isTextContentType(type),
                    RestServerBase.isBinaryContentType(type),
                    "Mismatch for type: " + type);
        }
    }
}
