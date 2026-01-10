package dev.patric.dungeonsreborn.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal, self-contained lint tool for the DSL syntax (no Bukkit/Paper runtime required).
 */
public final class DslLint {
  private DslLint() {
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: DslLint <path-to-script.es>");
      System.exit(2);
      return;
    }
    Path path = Path.of(args[0]);
    String source = Files.readString(path, StandardCharsets.UTF_8);
    try {
      lint(source, path.toString());
      System.out.println("DSL lint OK: " + path);
    } catch (IllegalArgumentException ex) {
      System.err.println("DSL lint FAILED: " + ex.getMessage());
      System.exit(1);
    }
  }

  public static void lint(String source, String path) {
    new Parser(source, path).parse();
  }

  private static final class Parser {
    private enum TokenType {
      IDENT,
      NUMBER,
      STRING,
      OP,
      COMP,
      LPAREN,
      RPAREN,
      COMMA,
      COLON,
      LBRACE,
      RBRACE,
      EQUALS,
      EOF
    }

    private record Token(TokenType type, String text, int line, int column) {
    }

    private final String input;
    private final String path;
    private int pos;
    private int line = 1;
    private int column = 1;
    private Token lookahead;
    private int onCastCount;
    private static final java.util.List<String> ALLOWED_FUNCTIONS = java.util.List.of(
        "min",
        "max",
        "clamp",
        "lerp",
        "rand",
        "abs",
        "floor",
        "ceil",
        "round");
    private static final java.util.Set<String> ALLOWED_FUNCTION_SET = java.util.Set.copyOf(ALLOWED_FUNCTIONS);

    Parser(String input, String path) {
      this.input = Objects.requireNonNull(input, "input");
      this.path = Objects.requireNonNull(path, "path");
      this.lookahead = nextToken();
    }

    void parse() {
      while (lookahead.type != TokenType.EOF) {
        if (lookahead.type == TokenType.IDENT && "macro".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          parseMacro();
          continue;
        }
        String handler = requireIdent("handler");
        String normalized = handler.toLowerCase(Locale.ROOT);
        if ("on_cast".equals(normalized) || "oncast".equals(normalized)) {
          onCastCount++;
        } else if (!"on_cancel".equals(normalized)
            && !"oncancel".equals(normalized)
            && !"on_end".equals(normalized)
            && !"onend".equals(normalized)
            && !"on_hit".equals(normalized)
            && !"onhit".equals(normalized)
            && !"finally".equals(normalized)
            && !"on_finish".equals(normalized)
            && !"onfinish".equals(normalized)
            && !"on_complete".equals(normalized)
            && !"oncomplete".equals(normalized)
            && !"on_done".equals(normalized)
            && !"ondone".equals(normalized)
            && !"on_cost_fail".equals(normalized)
            && !"oncostfail".equals(normalized)
            && !"on_cooldown_fail".equals(normalized)
            && !"oncooldownfail".equals(normalized)) {
          throw error("unsupported handler: " + handler);
        }
        parseBlock();
      }
      if (onCastCount == 0) {
        throw error("missing on_cast block");
      }
    }

    private void parseBlock() {
      consume(TokenType.LBRACE);
      while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
        parseStatement();
      }
      consume(TokenType.RBRACE);
    }

    private void parseStatement() {
      String name = requireIdent("statement");
      if ("call".equalsIgnoreCase(name)) {
        requireIdent("macro");
        parseAttributes();
        return;
      }
      if ("choice".equalsIgnoreCase(name)) {
        if (!(lookahead.type == TokenType.IDENT && "weighted".equalsIgnoreCase(lookahead.text))) {
          throw error("choice requires 'weighted' block");
        }
        consume(TokenType.IDENT);
        parseWeightedChoice();
        return;
      }
      if ("chance".equalsIgnoreCase(name)) {
        parseAttributes();
        parseBlock();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          parseBlock();
        }
        return;
      }
      if ("invoke".equalsIgnoreCase(name) || "invoke_ability".equalsIgnoreCase(name) || "invokeAbility".equalsIgnoreCase(name)) {
        if (lookahead.type == TokenType.STRING) {
          consume(TokenType.STRING);
        }
        parseAttributes();
        return;
      }
      if ("title".equalsIgnoreCase(name)) {
        if (lookahead.type == TokenType.STRING) {
          consume(TokenType.STRING);
        }
        parseAttributes();
        return;
      }
      if ("for_each_target".equalsIgnoreCase(name) || "targets".equalsIgnoreCase(name)
          || "raycast_hit_entity".equalsIgnoreCase(name) || "raycast_hit".equalsIgnoreCase(name)) {
        parseAttributes();
        parseBlock();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          parseBlock();
        }
        return;
      }
      if ("if".equalsIgnoreCase(name)) {
        parseCondition();
        parseBlock();
        if (lookahead.type == TokenType.IDENT && "else".equalsIgnoreCase(lookahead.text)) {
          consume(TokenType.IDENT);
          parseBlock();
        }
        return;
      }
      if ("set".equalsIgnoreCase(name)) {
        parseVarTarget();
        consume(TokenType.EQUALS);
        parseAssignValue();
        return;
      }
      if (lookahead.type == TokenType.EQUALS) {
        consume(TokenType.EQUALS);
        parseAssignValue();
        return;
      }
      if (lookahead.type == TokenType.STRING) {
        consume(TokenType.STRING);
      } else if (lookahead.type == TokenType.NUMBER
          || lookahead.type == TokenType.IDENT
          || lookahead.type == TokenType.OP
          || lookahead.type == TokenType.LPAREN) {
        if (!(lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS))) {
          parseExpression();
        }
      }
      while (lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS)) {
        consume(TokenType.IDENT);
        consume(TokenType.EQUALS);
        if (lookahead.type == TokenType.STRING) {
          consume(TokenType.STRING);
        } else {
          parseExpression();
        }
      }
      if (lookahead.type == TokenType.LBRACE) {
        parseBlock();
      }
    }

    private void parseAttributes() {
      while (lookahead.type == TokenType.IDENT && peekNextIs(TokenType.EQUALS)) {
        consume(TokenType.IDENT);
        consume(TokenType.EQUALS);
        if (lookahead.type == TokenType.STRING) {
          consume(TokenType.STRING);
        } else {
          parseExpression();
        }
      }
    }

    private void parseWeightedChoice() {
      consume(TokenType.LBRACE);
      while (lookahead.type != TokenType.RBRACE && lookahead.type != TokenType.EOF) {
        parseExpression();
        consume(TokenType.COLON);
        parseStatement();
      }
      consume(TokenType.RBRACE);
    }

    private void parseMacro() {
      requireIdent("macro");
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        if (lookahead.type != TokenType.RPAREN) {
          requireIdent("param");
          if (lookahead.type == TokenType.EQUALS) {
            consume(TokenType.EQUALS);
            parseAssignValue();
          }
          while (lookahead.type == TokenType.COMMA) {
            consume(TokenType.COMMA);
            requireIdent("param");
            if (lookahead.type == TokenType.EQUALS) {
              consume(TokenType.EQUALS);
              parseAssignValue();
            }
          }
        }
        consume(TokenType.RPAREN);
      }
      parseBlock();
    }

    private void parseCondition() {
      parseConditionValue();
      if (lookahead.type == TokenType.COMP) {
        consume(TokenType.COMP);
        parseConditionValue();
      }
    }

    private void parseConditionValue() {
      if (lookahead.type == TokenType.STRING) {
        consume(TokenType.STRING);
        return;
      }
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
        parseVarCall();
        return;
      }
      parseExpression();
    }

    private void parseAssignValue() {
      if (lookahead.type == TokenType.STRING) {
        consume(TokenType.STRING);
        return;
      }
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
        parseVarCall();
        return;
      }
      parseExpression();
    }

    private void parseVarTarget() {
      if (lookahead.type == TokenType.IDENT && "var".equalsIgnoreCase(lookahead.text) && peekNextIs(TokenType.LPAREN)) {
        parseVarCall();
        return;
      }
      consume(TokenType.IDENT);
    }

    private void parseVarCall() {
      consume(TokenType.IDENT);
      consume(TokenType.LPAREN);
      consume(TokenType.STRING);
      consume(TokenType.RPAREN);
    }

    private void parseExpression() {
      parseTerm();
      while (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        consume(TokenType.OP);
        parseTerm();
      }
    }

    private void parseTerm() {
      parsePower();
      while (lookahead.type == TokenType.OP
          && ("*".equals(lookahead.text) || "/".equals(lookahead.text) || "%".equals(lookahead.text))) {
        consume(TokenType.OP);
        parsePower();
      }
    }

    private void parsePower() {
      parseUnary();
      if (lookahead.type == TokenType.OP && "^".equals(lookahead.text)) {
        consume(TokenType.OP);
        parsePower();
      }
    }

    private void parseUnary() {
      if (lookahead.type == TokenType.OP && ("+".equals(lookahead.text) || "-".equals(lookahead.text))) {
        consume(TokenType.OP);
        parseUnary();
        return;
      }
      parsePrimary();
    }

    private void parsePrimary() {
      if (lookahead.type == TokenType.NUMBER) {
        consume(TokenType.NUMBER);
        return;
      }
      if (lookahead.type == TokenType.IDENT) {
        String name = lookahead.text;
        consume(TokenType.IDENT);
        if (lookahead.type == TokenType.LPAREN) {
          String lower = name.toLowerCase(Locale.ROOT);
          if (!ALLOWED_FUNCTION_SET.contains(lower)) {
            throw error("unknown function: " + name + " (use " + String.join("|", ALLOWED_FUNCTIONS) + ")");
          }
          consume(TokenType.LPAREN);
          if (lookahead.type != TokenType.RPAREN) {
            parseExpression();
            while (lookahead.type == TokenType.COMMA) {
              consume(TokenType.COMMA);
              parseExpression();
            }
          }
          consume(TokenType.RPAREN);
        }
        return;
      }
      if (lookahead.type == TokenType.LPAREN) {
        consume(TokenType.LPAREN);
        parseExpression();
        consume(TokenType.RPAREN);
        return;
      }
      throw error("expected expression");
    }

    private boolean peekNextIs(TokenType type) {
      int savedPos = pos;
      int savedLine = line;
      int savedCol = column;
      Token savedLookahead = lookahead;
      Token next = nextToken();
      pos = savedPos;
      line = savedLine;
      column = savedCol;
      lookahead = savedLookahead;
      return next.type == type;
    }

    private String requireIdent(String label) {
      Token t = lookahead;
      if (t.type != TokenType.IDENT) {
        throw error("expected " + label);
      }
      consume(TokenType.IDENT);
      return t.text;
    }

    private void consume(TokenType type) {
      if (lookahead.type != type) {
        throw error("expected " + type.name().toLowerCase(Locale.ROOT));
      }
      lookahead = nextToken();
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(path + " (" + line + ":" + column + "): " + message);
    }

    private Token nextToken() {
      int len = input.length();
      while (pos < len) {
        char c = input.charAt(pos);
        if (c == '#') {
          skipLineComment();
          continue;
        }
        if (Character.isWhitespace(c)) {
          advance(c);
          continue;
        }
        int startLine = line;
        int startCol = column;
        if (c == '{') {
          advance(c);
          return new Token(TokenType.LBRACE, "{", startLine, startCol);
        }
        if (c == '}') {
          advance(c);
          return new Token(TokenType.RBRACE, "}", startLine, startCol);
        }
        if (c == '(') {
          advance(c);
          return new Token(TokenType.LPAREN, "(", startLine, startCol);
        }
        if (c == ')') {
          advance(c);
          return new Token(TokenType.RPAREN, ")", startLine, startCol);
        }
        if (c == ',') {
          advance(c);
          return new Token(TokenType.COMMA, ",", startLine, startCol);
        }
        if (c == ':') {
          advance(c);
          return new Token(TokenType.COLON, ":", startLine, startCol);
        }
        if (c == '=') {
          if (peekChar() == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "==", startLine, startCol);
          }
          advance(c);
          return new Token(TokenType.EQUALS, "=", startLine, startCol);
        }
        if (c == '!') {
          if (peekChar() == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "!=", startLine, startCol);
          }
          throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): unexpected char: " + c);
        }
        if (c == '<' || c == '>') {
          char next = peekChar();
          if (next == '=') {
            advance(c);
            advance('=');
            return new Token(TokenType.COMP, "" + c + next, startLine, startCol);
          }
          advance(c);
          return new Token(TokenType.COMP, String.valueOf(c), startLine, startCol);
        }
        if (c == '"') {
          String text = readString();
          return new Token(TokenType.STRING, text, startLine, startCol);
        }
        if (c == '-' || c == '+' || Character.isDigit(c) || c == '.') {
          char next = peekChar();
          if ((c == '-' || c == '+') && !(Character.isDigit(next) || next == '.')) {
            advance(c);
            return new Token(TokenType.OP, String.valueOf(c), startLine, startCol);
          }
          String num = readNumber();
          return new Token(TokenType.NUMBER, num, startLine, startCol);
        }
        if (c == '*' || c == '/' || c == '%' || c == '^') {
          advance(c);
          return new Token(TokenType.OP, String.valueOf(c), startLine, startCol);
        }
        if (Character.isLetter(c) || c == '_') {
          String ident = readIdent();
          return new Token(TokenType.IDENT, ident, startLine, startCol);
        }
        throw new IllegalArgumentException(path + " (" + startLine + ":" + startCol + "): unexpected char: " + c);
      }
      return new Token(TokenType.EOF, "", line, column);
    }

    private void advance(char c) {
      pos++;
      if (c == '\n') {
        line++;
        column = 1;
      } else {
        column++;
      }
    }

    private void skipLineComment() {
      while (pos < input.length()) {
        char c = input.charAt(pos);
        advance(c);
        if (c == '\n') {
          return;
        }
      }
    }

    private String readString() {
      StringBuilder out = new StringBuilder();
      char quote = input.charAt(pos);
      advance(quote);
      while (pos < input.length()) {
        char c = input.charAt(pos);
        advance(c);
        if (c == quote) {
          return out.toString();
        }
        if (c == '\\' && pos < input.length()) {
          char next = input.charAt(pos);
          advance(next);
          switch (next) {
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case '"' -> out.append('"');
            case '\\' -> out.append('\\');
            default -> out.append(next);
          }
          continue;
        }
        out.append(c);
      }
      throw new IllegalArgumentException(path + " (" + line + ":" + column + "): unterminated string");
    }

    private String readNumber() {
      int start = pos;
      if (pos < input.length()) {
        char c = input.charAt(pos);
        if (c == '-' || c == '+') {
          advance(c);
        }
      }
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isDigit(c) || c == '.') {
          advance(c);
          continue;
        }
        break;
      }
      return input.substring(start, pos);
    }

    private String readIdent() {
      int start = pos;
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == ':') {
          advance(c);
          continue;
        }
        break;
      }
      return input.substring(start, pos);
    }

    private char peekChar() {
      if (pos + 1 >= input.length()) {
        return '\0';
      }
      return input.charAt(pos + 1);
    }
  }
}
