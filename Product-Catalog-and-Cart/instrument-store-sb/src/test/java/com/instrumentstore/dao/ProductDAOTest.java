package com.instrumentstore.dao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductDAOTest {

    @Test
    void likeWildcardsInASearchAreEscapedSoTheyMatchLiterally() {
        assertEquals("100\\%", ProductDAO.escapeLike("100%"));
        assertEquals("a\\_b", ProductDAO.escapeLike("a_b"));
        assertEquals("\\[x]", ProductDAO.escapeLike("[x]"));
        assertEquals("c:\\\\d", ProductDAO.escapeLike("c:\\d"));
        assertEquals("guitar", ProductDAO.escapeLike("guitar"));
    }
}
