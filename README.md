ChatCzGate
==========
IRC gateway for chat.cz written Kotlin

Special commands
----------------
**JOINR** - Join a room that contains special characters such as spaces. The 
room name is specified as is - without the # sign.

`Example: /JOINR Klidné povídání`

How build
---------
```
mvnw clean compile assembly:single
```

How run
-------
On Windows:
```
java -jar target\ChatCzGate.jar
```

On Linux:
```
java -jar target/ChatCzGate.jar
```