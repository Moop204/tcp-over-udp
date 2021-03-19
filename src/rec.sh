#!/bin/sh
javac Receiver.java -d build/ 
cd build
java Receiver 1234 ../pdfs/dank.pdf
