grammar Shell;

@header {
   package com.oracle;
} 

command:
    program=IDENTIFIER (' ' IDENTIFIER)*
    EOF;

IDENTIFIER: [a-z]+;
