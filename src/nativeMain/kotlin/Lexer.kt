val OPERATORS = listOf('+', '-', '/', '*', ';', '[', ']', '(', ')', '{', '}', '.', ':', ',')
val LOGIC = listOf('|', '&', '>', '<', '!', '#', '-', '=', '?')

data class Rule(
  var isSingle: Boolean,
  var name: String,
  var additionalCheck: ((String) -> Boolean)? = null,
  var rule: (Char) -> Boolean,
)

val rules = mutableListOf(
  Rule(true, "OPERATOR") { OPERATORS.contains(it) },
  Rule(
    false,
    "NUMBER",
    additionalCheck = {
      it.startsWith('0') || it.all { ch -> ch.isDigit() || ch == '.' }
    }
  ) { it.isDigit() || it == '.' || it == 'x' || it == 'b' },
  Rule(false, "LOGIC") { LOGIC.contains(it) },
  Rule(false, "ORDER") { it.isLetter() },
  Rule(false, "NEW-LINE") { it == '\n' || it == '\r' }
)

class Tokenizer(private val code: CharArray) {
  private var current = 0
  private var line = 1
  
  fun tokenizeCode(): MutableList<Token> {
    val tokens = mutableListOf<Token>()
    while (current < code.size) {
      var found = false
      var expr: String
      
      if (peek() == '"') {
        current++
        if (peek() == '"') {
          current++
          found = tokens.add(Token("STRING", "", 0, line))
        } else {
          expr = "${advance()}"
          while (current < code.size && (peek() != '"' || expr.last() == '\\')) expr += advance()
          if (current > code.size - 1 || advance() != '"') throw Error("[$line] Unterminated string")
          found = tokens.add(Token("STRING", expr.filter { it != '\\' }, expr.length, line))
        }
      }
      
      for (rule in rules) {
        if (found) break
        
        if (rule.isSingle) {
          if (rule.rule.invoke(peek()) && rule.additionalCheck?.invoke(peek().toString()) != false) {
            found = tokens.add(Token(rule.name, "${advance()}", 1, line))
            break
          }
        } else {
          if (rule.rule.invoke(peek())) {
            expr = code[current++].toString()
            if (rule.additionalCheck?.invoke(expr).let { it == false }) {
              current -= 1
            } else {
              while (current < code.size && rule.rule.invoke(peek()) && rule.additionalCheck?.invoke(expr)
                  .let { it == null || it == true }
              )
                expr = "$expr${advance()}"
              if (rule.name == "NEW-LINE") line++
              found = tokens.add(Token(rule.name, expr, expr.length, line))
              break
            }
          }
        }
      }
      
      if (!found) current++
    }
    return tokens.filter { it.type != "NEW-LINE" }.map { it.parse() }.toMutableList()
  }
  
  private fun peek(): Char = code[current]
  private fun advance(): Char = code[current++]
}

val keywords = listOf(
  "and",
  "or",
  "xor",
  "else",
  "false",
  "if",
  "let",
  "true",
  "fun",
  "return",
  "const",
  "to",
  "for",
  "class",
  "implement",
  "break",
  "continue",
  "import",
  "from",
  "as",
  "when",
  "static"
)
val operators = mapOf(
  '+' to "PLUS",
  '-' to "MINUS",
  '/' to "SLASH",
  '*' to "STAR",
  ';' to "SEMICOLON",
  ':' to "COLON",
  '.' to "DOT",
  ',' to "COMMA",
  '(' to "LEFT_PAREN",
  ')' to "RIGHT_PAREN",
  '{' to "LEFT_BRACE",
  '}' to "RIGHT_BRACE",
  '[' to "LEFT_BRACKET",
  ']' to "RIGHT_BRACKET"
)
val logic = listOf(
  LO("=", "EQUAL"), LO("==", "EQUAL_EQUAL"),
  LO("<", "LESS"), LO("<=", "LESS_EQUAL"),
  LO(">", "GREATER"), LO(">=", "GREATER_EQUAL"),
  LO("!", "BANG"), LO("!=", "BANG_EQUAL"),
  LO("||", "OR"), LO("&&", "AND"),
  LO("#>", "OBJ_START"), LO("<#", "OBJ_END"),
  LO("?>", "NULL_ELSE"), LO("<-", "GET"),
  LO(">-", "SET"), LO("->", "ARROW_RIGHT"),
  LO("<-", "ARROW_RIGHT")
).groupBy { it.value.length }

data class LO(val value: String, val name: String)

data class Token(var type: String, var value: String, val length: Int, val line: Int) {
  private var parsed: Boolean = false
  override fun toString(): String = "Token of type $type with value $value"
  fun parse(): Token {
    if (parsed) return this
    when (type) {
      "NUMBER" -> value = when {
        value.startsWith("0b") -> value.removePrefix("0b").toInt(radix = 2).toString()
        value.startsWith("0x") -> value.removePrefix("0x").toInt(radix = 16).toString()
        else -> value.toDouble().toString()
      }
      
      "ORDER" -> type = keywords.find { value.lowercase() == it }?.uppercase() ?: "IDENTIFIER"
      "OPERATOR" -> type = operators[value[0]] ?: "ERROR-XD"
      "LOGIC" -> {
        type = logic[value.length]?.find { value == it.value }?.name ?: "ERROR-WRONG-LOGIC"
      }
    }
    if (type == "ERROR-WRONG-LOGIC") throw Error("[$line] Wrong logic type, value: '$value   '")
    if(type =="ERROR-XD") throw Error("[$line]")
    return this.also { parsed = true }
  }
}