package com.ikedi.pos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandParserTest {

    @Test
    void parseEmptyOrWhitespaceGivesEmptyList() {
        assertTrue(CommandParser.parse(null).isEmpty());
        assertTrue(CommandParser.parse("").isEmpty());
        assertTrue(CommandParser.parse("   \t  \n  ").isEmpty());
    }

    @Test
    void parseSimpleUnquotedArguments() {
        List<String> tokens = CommandParser.parse("login manager ChangeMe123");
        assertEquals(List.of("login", "manager", "ChangeMe123"), tokens);
    }

    @Test
    void parseQuotedArgumentsPreservesInternalSpaces() {
        List<String> tokens = CommandParser.parse("product-add \"ST-RICE-01\" \"Dangote Parboiled Rice 5kg\" \"Grains & Staples\"");
        assertEquals(List.of("product-add", "ST-RICE-01", "Dangote Parboiled Rice 5kg", "Grains & Staples"), tokens);
    }

    @Test
    void parseEscapedQuotesInsideQuotedString() {
        List<String> tokens = CommandParser.parse("stock-receive \"SKU-1\" 10 \"Special \\\"Urgent\\\" Restock\"");
        assertEquals(List.of("stock-receive", "SKU-1", "10", "Special \"Urgent\" Restock"), tokens);
    }

    @Test
    void parseUnclosedQuoteThrowsValidationException() {
        assertThrows(ValidationException.class, () -> CommandParser.parse("product-add \"ST-RICE-01 unclosed string"));
    }
}
