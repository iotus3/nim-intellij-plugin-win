package com.laamella.nim;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

%%

%class _NimLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
  private int blockCommentDepth = 0;
%}

%state IN_BLOCK_COMMENT
%state IN_DOC_BLOCK_COMMENT
%state IN_TRIPLE_STRING
%state IN_TRIPLE_RAW_STRING

DIGIT        = [0-9]
HEXDIGIT     = [0-9a-fA-F]
OCTDIGIT     = [0-7]
BINDIGIT     = [01]
LETTER       = [a-zA-Z\u0080-\uFFFE]
IDENT_CONT   = [a-zA-Z0-9_\u0080-\uFFFE]

// Numeric suffixes
INT_SUFFIX   = "'" ("i8"|"i16"|"i32"|"i64"|"u"|"u8"|"u16"|"u32"|"u64")
FLOAT_SUFFIX = "'" ("f"|"f32"|"f64"|"d")
NUM_SUFFIX   = {INT_SUFFIX} | {FLOAT_SUFFIX}

// Number bases
HEX_NUM  = "0" [xX] {HEXDIGIT} ("_"? {HEXDIGIT})*
OCT_NUM  = "0" "o"  {OCTDIGIT} ("_"? {OCTDIGIT})*
BIN_NUM  = "0" [bB] {BINDIGIT} ("_"? {BINDIGIT})*
DEC_INT  = {DIGIT} ("_"? {DIGIT})*
FLOAT    = {DEC_INT} ("." {DIGIT} ("_"? {DIGIT})*)? ([eE] [+\-]? {DEC_INT})?

// Keywords (Nim is case-insensitive for keywords; listed lowercase — matched case-insensitively via JFlex %ignorecase is not ideal here since identifiers are case-sensitive after first char; we match keywords explicitly then fall through to IDENTIFIER)
KEYWORD = "addr"|"and"|"as"|"asm"|"bind"|"block"|"break"|"case"|"cast"|"concept"|"const"|"continue"|"converter"|"defer"|"discard"|"distinct"|"div"|"do"|"elif"|"else"|"end"|"enum"|"except"|"export"|"finally"|"for"|"from"|"func"|"if"|"import"|"in"|"include"|"interface"|"is"|"isnot"|"iterator"|"let"|"macro"|"method"|"mixin"|"mod"|"nil"|"not"|"notin"|"object"|"of"|"or"|"out"|"proc"|"ptr"|"raise"|"ref"|"return"|"shl"|"shr"|"static"|"template"|"try"|"tuple"|"type"|"using"|"var"|"when"|"while"|"xor"|"yield"

%%

// --- Whitespace ---
<YYINITIAL> [ \t\r\n]+                     { return TokenType.WHITE_SPACE; }

// --- Doc block comment ##[ ... ]## ---
<YYINITIAL> "##["                          { blockCommentDepth = 1; yybegin(IN_DOC_BLOCK_COMMENT); }
<IN_DOC_BLOCK_COMMENT> "##["               { blockCommentDepth++; }
<IN_DOC_BLOCK_COMMENT> "]##"               { if (--blockCommentDepth == 0) { yybegin(YYINITIAL); return NimTokenTypes.DOC_COMMENT; } }
<IN_DOC_BLOCK_COMMENT> [^]                 { }

// --- Block comment #[ ... ]# ---
<YYINITIAL> "#["                           { blockCommentDepth = 1; yybegin(IN_BLOCK_COMMENT); }
<IN_BLOCK_COMMENT> "#["                    { blockCommentDepth++; }
<IN_BLOCK_COMMENT> "]#"                    { if (--blockCommentDepth == 0) { yybegin(YYINITIAL); return NimTokenTypes.BLOCK_COMMENT; } }
<IN_BLOCK_COMMENT> [^]                     { }

// --- Doc comment ## ... ---
<YYINITIAL> "##" [^\n]*                    { return NimTokenTypes.DOC_COMMENT; }

// --- Line comment # ... (must not match #[ block comment or ## doc comment openers) ---
<YYINITIAL> "#" [^\[\n#] [^\n]*            { return NimTokenTypes.LINE_COMMENT; }
<YYINITIAL> "#"                             { return NimTokenTypes.LINE_COMMENT; }

// --- Triple raw string r""" ... """ or R""" ... """ ---
<YYINITIAL> [rR] \"\"\"                    { yybegin(IN_TRIPLE_RAW_STRING); }
<IN_TRIPLE_RAW_STRING> \"\"\"              { yybegin(YYINITIAL); return NimTokenTypes.STRING; }
<IN_TRIPLE_RAW_STRING> [^]                 { }

// --- Triple string """ ... """ ---
<YYINITIAL> \"\"\"                         { yybegin(IN_TRIPLE_STRING); }
<IN_TRIPLE_STRING> \"\"\"                  { yybegin(YYINITIAL); return NimTokenTypes.STRING; }
<IN_TRIPLE_STRING> [^]                     { }

// --- Raw string r"..." or R"..." (doubled "" = embedded quote) ---
<YYINITIAL> [rR] \" ( \"\" | [^\"\n] )* \" { return NimTokenTypes.STRING; }

// --- Generalized string: identifier"""...""" ---
<YYINITIAL> {LETTER} {IDENT_CONT}* \"\"\"  { yybegin(IN_TRIPLE_STRING); }

// --- Generalized string: identifier"..." ---
<YYINITIAL> {LETTER} {IDENT_CONT}* \" ( \\. | [^\"\n\\] )* \" { return NimTokenTypes.STRING; }

// --- Normal string "..." ---
<YYINITIAL> \" ( \\. | [^\"\n\\] )* \"    { return NimTokenTypes.STRING; }

// --- Character literal '.' ---
<YYINITIAL> \' ( \\. | [^\'\n] ) \'       { return NimTokenTypes.CHAR; }

// --- Numbers ---
<YYINITIAL> {HEX_NUM} {NUM_SUFFIX}?       { return NimTokenTypes.NUMBER; }
<YYINITIAL> {OCT_NUM} {NUM_SUFFIX}?       { return NimTokenTypes.NUMBER; }
<YYINITIAL> {BIN_NUM} {NUM_SUFFIX}?       { return NimTokenTypes.NUMBER; }
<YYINITIAL> {FLOAT}   {NUM_SUFFIX}?       { return NimTokenTypes.NUMBER; }

// --- Backtick identifier `...` ---
<YYINITIAL> "`" [^`]+ "`"                 { return NimTokenTypes.IDENTIFIER; }

// --- Keywords (matched before identifier) ---
<YYINITIAL> {KEYWORD} / [^a-zA-Z0-9_\u0080-\uFFFE] { return NimTokenTypes.KEYWORD; }

// --- Identifiers ---
<YYINITIAL> {LETTER} {IDENT_CONT}*        { return NimTokenTypes.IDENTIFIER; }

// --- Brackets ---
<YYINITIAL> "("                            { return NimTokenTypes.LPAREN; }
<YYINITIAL> ")"                            { return NimTokenTypes.RPAREN; }
<YYINITIAL> "["                            { return NimTokenTypes.LBRACKET; }
<YYINITIAL> "]"                            { return NimTokenTypes.RBRACKET; }
<YYINITIAL> "{"                            { return NimTokenTypes.LBRACE; }
<YYINITIAL> "}"                            { return NimTokenTypes.RBRACE; }

// --- Catch-all operator ---
<YYINITIAL> [^]                            { return NimTokenTypes.OPERATOR; }
