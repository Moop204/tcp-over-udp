#!/bin/sh
javac Sender.java -d build/
cd build
java Sender 127.0.0.1 1234 ../pdfs/test2.pdf 500 50 4 0.1 0.1 0.1 0.1 4 0 0 300 
