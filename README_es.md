# Beaconator

[![Build](https://github.com/CodeW4VE/Beaconator/actions/workflows/build.yml/badge.svg)](https://github.com/CodeW4VE/Beaconator/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21-green.svg)](https://fabricmc.net/)

Mod cliente de Fabric para planificar y construir **perímetros de beacons**.

Pones un beacon central, subís la rueda para agrandar la retícula, y Beaconator calcula dónde
va cada beacon, dibuja exactamente cuánto cubre cada uno y lleva la cuenta de los que ya
construiste.

[English](README.md)

## Por qué

Los mods de esquemas hacen el trabajo, pero para esto molestan más de lo que ayudan:

- Resaltan todos los bloques del esquema, aire y tierra incluidos, y eso es ruido que hay que aprender a ignorar.
- Ninguno muestra el área que cubre un beacon, así que los huecos del perímetro los encontrás caminando dentro de ellos.
- Distinguir lo construido de lo que falta es comparar a ojo contra el esquema.

Beaconator solo hace perímetros, así que puede hacerlos bien.

## Qué hace

- **Un mapa sobre el que planificar.** El mod dibuja su propio mapa con los chunks que tenés cargados y le pone la retícula encima: ahí decidís con clicks cuáles nodos entran, cuáles quedan fuera y cuáles se eliminan. Así es como se planifica un perímetro de verdad, mirando la forma desde arriba y no forzando la vista contra cajas a cien bloques de distancia.
- **Retícula desde un punto.** Ponés el centro y la rueda recorre anillos concéntricos: 1 nodo, 9, 25, 49. Los lados se pueden estirar por separado cuando el perímetro no es cuadrado.
- **Cobertura real.** Cada nodo se dibuja con el volumen en el que vanilla aplica los efectos: `2r + 1` de lado, `r` hacia abajo y sin límite hacia arriba. No es un cubo.
- **De 1 a 5 beacons por nodo** sobre una sola pirámide compartida, para tener todos los efectos primarios a la vez. Los tamaños de capa y el conteo de bloques salen solos.
- **Estados de nodo.** Click izquierdo lo saca del plan, click derecho lo marca como fuera del perímetro, lo que le pone un cristal negro encima para que se distinga de lejos.
- **Escaneo en vivo.** Los nodos se ponen verdes solos según los construís, y las pirámides a medias van tirando a verde conforme entran los bloques.
- **Lista de materiales** de lo que pide el plan y lo que falta.
- **Colocación asistida** que elige el bloque correcto de tu hotbar y no te deja poner bloques del plan donde el plan no pide nada.
- **Filtro de capas** para trabajar una hilera a la vez.
- **Importar y exportar litematics**, para retomar un perímetro que ya construiste y pasarle el esquema a quien no use este mod. Si tenés Litematica instalado, el easy place sigue su toggle en vez de pelearse con él.
- **Inglés y español**, cambiado desde la pantalla del mod y no desde el idioma del juego.
- **Todas las teclas se pueden cambiar** desde la pestaña Keys, y la pantalla también se abre desde Mod Menu.

## Requisitos

- Minecraft 1.21
- Fabric Loader 0.16 o superior
- Fabric API

Solo cliente. No manda nada al servidor ni hace falta instalar nada allá.

## Para empezar

Con **shift + B** se abre la pantalla del mod, o le asignás la tecla que quieras. Ahí está
todo: el mapa, los ajustes de retícula, los bloques, la lista de materiales y la pantalla.

- **New plan here**, en la pestaña Plan, empieza un plan centrado donde estás parado.
- **Detect from world** lee un perímetro que ya está construido, directo de los chunks
  cargados y con coordenadas reales. Es la forma de retomar uno existente.

En la pestaña **Map**: arrastrás para mover, rueda para zoom, click izquierdo elimina un nodo y
click derecho lo marca como fuera del perímetro. El terreno se va rellenando conforme volás por
encima y se conserva entre sesiones.

Marcar doscientos nodos de uno en uno no le gusta a nadie, así que arrastrando se hace en bloque:

| Control | Qué hace |
|---------|----------|
| Shift + arrastrar con el izquierdo | Elimina del plan todo lo que quede en el rectángulo |
| Shift + arrastrar con el derecho | Marca como fuera del perímetro todo lo del rectángulo |
| Ctrl + arrastrar | Devuelve a pendiente todo lo del rectángulo |
| Ctrl + Z, o el botón Deshacer | Devuelve el último cambio, sea un nodo o un rectángulo entero |

En el mundo, con el modo edición encendido (`B`):

| Control | Qué hace |
|---------|----------|
| Rueda | Agranda o achica la retícula |
| Shift + rueda | Beacons por nodo, de 1 a 5 |
| Ctrl + rueda | Ajusta la separación |
| Click izquierdo en un nodo | Lo saca del plan, o lo devuelve |
| Click derecho en un nodo | Lo marca como fuera del perímetro, o lo devuelve |

## Teclas

Todas se pueden cambiar, desde los controles del juego o desde la pestaña **Keys** del propio
mod. Solo la primera viene con tecla puesta.

| Tecla | Por defecto |
|-------|-------------|
| Modo edición, con shift abre la pantalla | `B` |
| Abrir la pantalla | sin asignar |
| Poner el centro donde estás | sin asignar |
| Encender o apagar el render | sin asignar |
| Encender o apagar easy place | sin asignar |
| Capa arriba · Capa abajo | sin asignar |
| Escanear el mundo | sin asignar |

## Comandos

Todo cuelga de `/bea` (o `/beaconator`). Los mensajes de los comandos están en inglés; la
pantalla y el HUD sí hablan español.

| Comando | Qué hace |
|---------|----------|
| `/bea gui` | Abre la pantalla |
| `/bea new <nombre>` | Empieza un plan centrado donde estás |
| `/bea detect [nombre] [radio]` | Arma el plan leyendo los beacons ya construidos |
| `/bea open <nombre>` · `list` · `save` · `delete <nombre>` · `close` | Planes guardados |
| `/bea info` | Todo sobre el plan actual, materiales incluidos |
| `/bea ring <n>` | Deja la retícula en un cuadrado concéntrico |
| `/bea side <north\|south\|east\|west> <n>` | Estira o encoge un lado |
| `/bea beacons <1-5>` | Beacons por nodo |
| `/bea level <1-4>` | Nivel de pirámide, que fija el alcance |
| `/bea spacing <n>` · `spacing auto` | Separación entre nodos |
| `/bea axis <x\|z>` | Hacia dónde crecen las filas de beacons |
| `/bea block pyramid <id>` · `block marker <id>` | Bloques con los que se construye |
| `/bea marker <on\|off>` | Si los nodos excluidos llevan bloque marcador de verdad |
| `/bea scan` | Compara el plan entero contra el mundo |
| `/bea materials [node]` | Lo que hace falta y lo que falta por poner |
| `/bea state <pending\|excluded\|removed>` | Cambia el nodo al que apuntás |
| `/bea fill <estado> <desdeI> <desdeJ> <hastaI> <hastaJ>` | Cambia un rectángulo de nodos |
| `/bea layer <all\|here\|y [hastaY]>` | Filtro de capas |
| `/bea easyplace <on\|off>` | Colocación asistida |
| `/bea import <archivo>` · `export [archivo]` | Litematics, desde `schematics/` |
| `/bea move <dx> <dy> <dz>` · `center [x y z]` | Mover el plan |
| `/bea render <on\|off>` · `style <slab\|floor\|full>` · `hud <on\|off>` | Pantalla |

## La matemática, por si te interesa

Un beacon con `level` capas de pirámide alcanza `10 * level + 10` bloques, así que una pirámide
de nivel 4 cubre un cuadrado de `101` de lado. Con esa separación la cobertura encaja exacta,
sin solape ni huecos. Cualquier cosa más ancha deja franjas de suelo sin efecto, que Beaconator
pinta de rojo para que las veas antes de estar parado en una.

Con `n` beacons en fila sobre una pirámide, la capa `k` pasa a ser `(2k + 1)` por `(2k + n)`.
Cinco beacons de nivel 4 necesitan una base de 9x13, 260 bloques, en vez de cinco pirámides
separadas de 9x9.

La matemática vive en `xyz.w4ve.beaconator.model`, no tiene Minecraft dentro, y está cubierta
por tests contra un perímetro real de 208 nodos.

## Compilar

```
JAVA_HOME=/ruta/a/jdk-21 ./gradlew build
```

El jar sale en `build/libs/`.

## Licencia

MIT. El comportamiento está inspirado en los mods de esquemas y de HUD que usa todo el mundo,
pero no se copió código de ninguno.
