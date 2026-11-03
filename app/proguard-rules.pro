# Regras do R8 para as releases (ver app/build.gradle.kts).
#
# A otimização só abrange as bibliotecas (packageScope). Ao mudar classes de pacote, o R8 punha uma
# classe a estender outra que ficou privada do pacote dela (kotlin.sequences): IllegalAccessError
# logo ao abrir a app. Cada classe fica no seu pacote.
-dontrepackage
