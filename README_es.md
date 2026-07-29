# Beaconator

[![Build](https://github.com/CodeW4VE/Beaconator/actions/workflows/build.yml/badge.svg)](https://github.com/CodeW4VE/Beaconator/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21--1.21.11-green.svg)](https://fabricmc.net/)

Mod de Fabric para planificar y construir **perímetros de beacons**. Funciona solo en el cliente;
poné el mismo jar en el servidor y todo el equipo comparte un plan.

Pones un beacon central, subís la rueda para agrandar la retícula, y Beaconator calcula dónde
va cada beacon, dibuja exactamente cuánto cubre cada uno y lleva la cuenta de los que ya
construiste.

![Un perímetro desde el aire, con un haz sobre cada beacon](docs/img/01-beams.jpg)

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
- **De 1 a 6 beacons por nodo** sobre una sola pirámide compartida, para tener todos los efectos primarios a la vez. El mod elige la **forma más barata**: cuatro beacons en cuadrado gastan 216 bloques por nodo en vez de los 236 de ponerlos en fila.
- **Un plan compartido por el servidor.** Poné el mismo jar en un servidor Fabric y el plan es de todos: te llega al entrar, y cada nodo que alguien coloca, excluye o elimina aparece en vivo para el resto.
- **Estados de nodo.** Click izquierdo lo saca del plan, click derecho lo marca como fuera del perímetro, lo que le pone un cristal negro encima para que se distinga de lejos.
- **Escaneo en vivo.** Los nodos se ponen verdes solos según los construís. Los que están a medias quedan **amarillos**, incluida la pirámide terminada a la que le falta un beacon: esa parece lista desde lejos y te enterás cuando ya spawnean mobs.
- **Lista de materiales** de lo que pide el plan y lo que falta.
- **Colocación asistida** que elige el bloque correcto de tu hotbar y no te deja poner bloques del plan donde el plan no pide nada.
- **Filtro de capas** para trabajar una hilera a la vez.
- **Importar y exportar litematics**, para retomar un perímetro que ya construiste y pasarle el esquema a quien no use este mod. Si tenés Litematica instalado, el easy place sigue su toggle en vez de pelearse con él.
- **Inglés y español**, cambiado desde la pantalla del mod y no desde el idioma del juego.
- **Todas las teclas se pueden cambiar**, con modificadores: `G`, `Shift + G` y `Ctrl + Shift + G` son tres teclas distintas.

## Requisitos

- Minecraft **1.21** a **1.21.8**, y **1.21.11**, un jar por version
- Fabric Loader 0.16 o superior
- [Fabric API](https://modrinth.com/mod/fabric-api)

Tirá el jar en `mods/` y listo. Opcional: poné **el mismo jar** en el servidor Fabric para los
planes compartidos. Si no lo hacés, no cambia nada.

## ¿Nunca lo usaste? Empezá acá

Beaconator sirve para una cosa: cubrir un área grande de beacons, pareja, sin huecos, y saber
cuánto te falta. Si estás spawn proofeando un perímetro, este es el mod.

**1. Abrí la pantalla.** **Shift + B**. Eso es toda la interfaz: pestañas arriba, Listo abajo.
`B` solo enciende y apaga el modo edición.

**2. Hacé un plan.** Parate más o menos en el medio del área y en la pestaña **Plan** dale a
**Plan nuevo aquí**. Si el perímetro ya está a medias, dale a **Detectar del mundo** y el mod lee
los beacons que ya están puestos, con sus coordenadas reales.

**3. Dale forma en el mapa.** La pestaña **Mapa** es el mapa del propio mod, dibujado a un píxel
por bloque con los chunks que fuiste cargando, y con tu retícula encima.

![La pestaña del mapa con toda la retícula](docs/img/02-map.jpg)

Para moverte por él:

| Control | Qué hace |
|---------|----------|
| Arrastrar | Mueve el mapa |
| Rueda | Zoom |
| Botón **Encajar** | Aleja hasta que entre el plan entero |
| Botón **Centrar en mí** | Salta a donde estás parado |
| Click izquierdo en un nodo | Lo elimina del plan (no se construye) |
| Click derecho en un nodo | Lo marca excluido (se construye, pero no es del perímetro) |
| Shift + arrastrar, izquierdo | Elimina todo el rectángulo |
| Shift + arrastrar, derecho | Excluye todo el rectángulo |
| Ctrl + arrastrar | Devuelve el rectángulo a pendiente |
| Ctrl + Z, o **Deshacer** | Deshace lo último, un nodo o un rectángulo entero |
| Flechas | Corren la retícula un bloque (shift 5, ctrl 16) |
| Av pág / Re pág | Mueven la retícula en Y |

Cerrar la pantalla no pierde nada: el plan se guarda solo, y se reabre la próxima vez que entrás
a ese servidor.

**4. Configuralo.** En la pestaña **Retícula**: beacons por nodo, nivel de la pirámide, separación
y hasta dónde llega. Dejá **La separación sigue al nivel** encendido y la cobertura encaja exacta,
sin solapes ni huecos. El renglón de abajo te dice si encaja.

**5. Construilo.** Ahora sí, a picar. En el mundo tenés:

- Un **haz sobre cada posición de beacon** que sube al cielo como uno de verdad. **Rojo** es que
  no está construido, **amarillo** que está empezado pero sin terminar (incluida la pirámide
  entera a la que le falta un beacon), **verde** que está listo, gris que está excluido. Con **G**
  los apagás cuando estorban.

  ![Haces rojos sobre lo que falta, verdes sobre lo hecho](docs/img/04-beams-close.jpg)

- La **cobertura** real de cada beacon, así los huecos se ven en vez de ser teóricos.
- Una **lista de materiales** de lo que falta, en el HUD y en su pestaña.
- Los nodos se ponen verdes **solos** a medida que los construís. Vos no le decís nada al mod.

**6. En equipo.** Si el servidor tiene el mod, aparece una pestaña **Servidor**. Dale a
**Compartir el mío** para subir tu plan; los demás lo abren de la lista. A partir de ahí, cada
nodo que alguien termina lo ven todos, en vivo. El HUD pone `[compartido]` cuando lo que marcás
sale para afuera.

## Teclas

Todas se pueden cambiar, desde los controles del juego o desde la pestaña **Teclas** del mod. Las
teclas admiten modificadores, así que `G`, `Shift + G` y `Ctrl + Shift + G` son tres cosas
distintas.

| Tecla | Por defecto |
|-------|-------------|
| Modo edición | `B` |
| Abrir la pantalla | `Shift + B` |
| Haces sí o no | `G` |
| Poner el centro donde estás | sin asignar |
| Render sí o no | sin asignar |
| Easy place sí o no | sin asignar |
| Capa arriba · Capa abajo | sin asignar |
| Escanear el mundo | sin asignar |
| Deshacer | sin asignar |

Con el modo edición encendido, en el mundo:

| Control | Qué hace |
|---------|----------|
| Rueda | Agranda o achica la retícula |
| Shift + rueda | Beacons por nodo, de 1 a 6 |
| Ctrl + rueda | Ajusta la separación |
| Click izquierdo en un nodo | Lo saca del plan, o lo devuelve |
| Click derecho en un nodo | Lo marca excluido, o lo devuelve |

## Comandos

Tres, porque todo lo demás es un botón que además te muestra el valor actual, que es justo lo
que un comando no puede hacer.

| Comando | Qué hace |
|---------|----------|
| `/bea` o `/bea gui` | Abre la pantalla |
| `/bea share` | Sube el plan abierto al servidor |

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
