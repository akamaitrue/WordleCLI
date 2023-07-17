# Wordle

## Description
CLI implementation of Wordle, assigned as final project for Networks & Lab3 at UniPi ([course description here](https://esami.unipi.it/programma.php?c=55555))\
If you have never heard about Wordle, take a look [here](https://www.youtube.com/watch?v=WnWPXZ6vQB8&pp=ygUPd29yZGxlIHR1dG9yaWFs) to see how it works\
\
It was required to write a detailed and deeply explained explanation of the code, which you can find [here](https://github.com/akamaitrue/WordleCLI/blob/main/Relazione%20Wordle.pdf).\
I won't discuss details here, feel free to read [the longer description](https://github.com/akamaitrue/WordleCLI/blob/main/Relazione%20Wordle.pdf) if you're interested.\
TODO: rewrite it in LaTeX and remove redundant information\
\
The main skills required for this project are: Java MultiThreading, Java Socket Programming, Unicast-Multicast-Broadcast

## How to use
1. `git clone https://github.com/akamaitrue/WordleCLI.git`
2. `cd Wordle`
3. `javac -cp ./lib/gson-2.9.1.jar -d ./ @./settings/sources.txt`
4. `java -jar --enable-preview ServerWordle.jar`
5. `java -jar --enable-preview ClientWordle.jar`

Goes without saying that you can spam as many clients as you want, each representing a different player (make sure to edit [user info](https://github.com/akamaitrue/WordleCLI/blob/main/user_settings.json) accordingly for changes to take effect).\
This is the core and main point of the project, meaning to handle multiple clients playing simultaneously and keeping their info consistent and updated
