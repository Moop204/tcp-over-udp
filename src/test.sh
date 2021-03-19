#!/bin/sh

sh rec.sh & 

sh sen.sh

xxd pdfs/Lab4.pdf > p1.txt
xxd pdfs/dank.pdf > p2.txt

diff -a Lab4.pdf dank.pdf
