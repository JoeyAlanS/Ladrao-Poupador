# 🦹 Agente Ladrão — Documentação Completa

> Agente inteligente para simulação de labirinto multiagente, desenvolvido em Java.
> Implementa busca A*, memória espacial acumulativa e sistema de tomada de decisão probabilístico.

---

## 📌 Visão Geral

O **Ladrão** é um agente autônomo que navega em um labirinto 30×30, perseguindo agentes chamados **Poupadores** para roubar suas moedas. Ele combina:

- **Memória espacial** → lembra o mapa mesmo sem visão direta
- **Busca com A\*** → planeja caminhos ótimos
- **Sensores** → visão (5×5), olfato (3×3) e contador de moedas
- **Tomada de decisão probabilística** → sistema de scores + roulette wheel

---

## 🤖 É um Autômato?

**Sim, parcialmente.** O Ladrão é um **agente reativo com memória** (também chamado de *agente baseado em modelo*), que é uma categoria acima de um autômato puro.

| Característica | Autômato Finito | Ladrão |
|---|---|---|
| Estados fixos | ✅ Sim | ⚠️ Dinâmicos (baseados em percepção) |
| Memória | ❌ Limitada ao estado | ✅ Matriz 30×30 acumulativa |
| Transições determinísticas | ✅ Sim | ❌ Probabilísticas (roulette wheel) |
| Percepção do ambiente | ❌ Não | ✅ Visão + Olfato + Moedas |
| Planejamento | ❌ Não | ✅ A* com grafo dinâmico |

**Conclusão:** É um **agente inteligente reativo com modelo interno**, não um autômato finito clássico. A cada turno ele percebe o ambiente, atualiza sua memória e decide a melhor ação — comportamento característico de sistemas de IA multiagente.

---

## 🏗️ Estrutura de Classes

```
agente/
├── HScore          → Heurística de Manhattan Distance
├── Node            → Nó da árvore de busca A*
├── Graph           → Grafo do labirinto + algoritmo A*
└── Ladrao          → Agente principal (extends ProgramaLadrao)
```

---

## 📐 Classes Auxiliares

### `HScore`
Responsável exclusivamente pela heurística de distância utilizada no A*.

**Método:**
```
calculateManhattanDistance(int[] origin, int[] destiny) → int
```
Fórmula: `|x1 - x2| + |y1 - y2|`

Representa o número mínimo de movimentos (sem diagonais) entre dois pontos.

---

### `Node`
Representa um nó na árvore de busca do A*.

**Atributos:**
| Atributo | Tipo | Descrição |
|---|---|---|
| `label` | `String` | Rótulo no formato `"x:y"` |
| `root` | `Node` | Referência ao nó pai (para reconstrução de caminho) |

Forma uma **estrutura de árvore encadeada** que permite rastrear o caminho percorrido de volta até a origem.

---

### `Graph`
Grafo direcionado que representa o labirinto. Usa **lista de adjacência** via `Map<String, Map<String, Integer>>`.

**Estrutura interna:**
```
vertexes = {
  "5:10" → { "5:11" → 3, "6:10" → 2 },
  "5:11" → { "5:10" → 1 },
  ...
}
```
Cada aresta armazena a **direção de movimento** (1=cima, 2=direita, 3=baixo, 4=esquerda).

**Métodos:**
| Método | Descrição |
|---|---|
| `addVertex(label)` | Adiciona vértice ao grafo |
| `addEdge(origin, destiny, direction)` | Adiciona aresta direcionada com código de movimento |
| `getNeighbors(label)` | Retorna vizinhos de um vértice |
| `convertLabelToCoordinates(label)` | `"5:10"` → `[5, 10]` |
| `convertCoordinatesToLabel(coords)` | `[5, 10]` → `"5:10"` |
| `buildPathFromNodes(path, destinyLabel)` | Reconstrói caminho percorrendo nós pai |
| `findPathAStar(origin, destiny)` | Executa busca A* completa |

---

## 🧠 Classe Principal — `Ladrao`

Extends `ProgramaLadrao` (interface com o simulador).

### Atributos e Onde São Armazenados

| Atributo | Tipo | Onde fica | Descrição |
|---|---|---|---|
| `knownField` | `int[30][30]` | **Heap (objeto)** | Mapa mental 30×30 do labirinto inteiro |
| `graph` | `Graph` | **Heap (objeto)** | Grafo dinâmico atual (visão ou memória) |
| `explorationObjectiveLocation` | `String` | **Heap (objeto)** | Rótulo `"x:y"` do objetivo atual de exploração |
| `targetRefreshRate` | `HashMap<Integer,Integer>` | **Heap (objeto)** | Cooldown de roubo por Poupador |
| `previousMoneyOnHold` | `int` | **Stack (primitivo)** | Saldo anterior para detectar roubo |
| `nonVisitableLands` | `ArrayList<Integer>` | **Heap (objeto)** | Códigos de terrenos intransponíveis |

---

### Codificação do Mapa (`knownField`)

Cada célula da matriz `int[30][30]` pode conter:

| Valor | Significado |
|---|---|
| `-2` | **Desconhecido** — nunca visitado |
| `-1` | Fora de alcance / invisível |
| `0` | **Espaço vazio** — caminhável |
| `1` | **Parede** — intransponível |
| `3` | Banco |
| `4` | Moeda solta |
| `5` | Pastilha do Poder |
| `100` | Poupador ID 100 |
| `110` | Poupador ID 110 |
| `200–230` | Outros Ladrões |

---

### Sensores Disponíveis

| Sensor | Retorno | Área | Descrição |
|---|---|---|---|
| `getCurrentVision()` | `int[25]` | 5×5 | Visão completa ao redor |
| `getSmellSensor()` | `int[9]` | 3×3 | Intensidade de cheiro de Poupadores |
| `getCurrentMoney()` | `int` | — | Total de moedas acumuladas |
| `getCurrentPosition()` | `int[2]` | — | Coordenadas `[x, y]` atuais |

---

## ⚙️ Fluxo de Execução — `acao()` (chamado a cada turno)

```
acao()
  │
  ├─► updateMemoryWithVision()   → Sincroniza visão 5x5 com knownField
  │
  ├─► updateTargetCooldown()     → Decrementa cooldowns de roubo
  │
  └─► decideNextActionTarget()   → Decide e executa ação
            │
            ├─► calculateActionScores()    → Pontua cada ação
            ├─► normalize()               → Converte em probabilidades
            ├─► rouletteWheelSelection()  → Seleciona ação por roleta
            │
            ├─► [0] chaseTarget()         → Perseguir Poupador visível
            ├─► [1] trackBySmell()        → Rastrear pelo olfato
            └─► [2] exploreEnvironment()  → Explorar áreas desconhecidas
```

---

## 🎯 Sistema de Decisão (Scoring + Roulette Wheel)

A tomada de decisão **não é determinística** — usa um sistema probabilístico em 3 etapas:

### Etapa 1: Coleta de Situação
```
visibleTarget   → Poupador visível sem cooldown? (coordenadas ou null)
hasSmell        → getSmellSensor() tem algum valor ≠ 0?
hasUnknownArea  → Existe algum tile -2 em raio de 10 tiles?
```

### Etapa 2: Cálculo de Scores (`calculateActionScores`)

| Condição | Ação beneficiada | Score adicionado |
|---|---|---|
| Alvo visível sem cooldown | PERSEGUIR | +3.0 base |
| Proximidade do alvo | PERSEGUIR | +0.0 a +2.0 (bônus) |
| Cheiro detectado | RASTREAR | +2.5 base |
| Área desconhecida próxima | EXPLORAR | +2.0 |
| Nenhuma condição ativa | Todos | Distribuição padrão |
| PERSEGUIR muito dominante | Balanceamento | -10% perseguir, +0.3 outros |

### Etapa 3: Roulette Wheel
```
Scores normalizados → probabilidades (soma = 1.0)
Roll aleatório [0.0, 1.0] → ação escolhida

Ex: perseguir=0.6, rastrear=0.25, explorar=0.15
    roll=0.45 → PERSEGUIR (pois 0.45 < 0.6)
    roll=0.72 → RASTREAR  (pois 0.6 < 0.72 < 0.85)
    roll=0.91 → EXPLORAR
```

Cada ação escolhida tem **fallbacks** em cascata:
- PERSEGUIR falha → tenta RASTREAR → tenta EXPLORAR
- RASTREAR falha → tenta PERSEGUIR → tenta EXPLORAR
- EXPLORAR → sempre possível (é o fallback universal)

---

## 🗺️ Construção de Grafos

O Ladrão mantém **dois modos** de construção de grafo:

### `buildGraphFromVision()` — Grafo Local
- Cobre apenas a área **5×5** visível
- Usado em: `chaseTarget()` (perseguição em tempo real)
- Mais rápido, mas limitado em alcance

### `buildGraphFromMemory()` — Grafo Global
- Cobre **todo o labirinto 30×30** memorizado
- Usado em: `exploreEnvironment()` (planejamento de longo prazo)
- Mais lento, mas permite rotas completas

**Diferença de direções:**
- Visão: direções `1, 2, 3, 4` (ordem crescente)
- Memória: direções `4, 3, 2, 1` (ordem decrescente — coerência interna do algoritmo)

---

## 🔍 Algoritmo A* (`findPathAStar`)

Implementação clássica com:

- **openSet** → `HashMap<String, Integer>`: nós candidatos e suas distâncias
- **closedSet** → `ArrayList<String>`: nós já explorados
- **path** → `ArrayList<Node>`: árvore de busca com referências pai

**Heurística:** Manhattan Distance até o destino

**Desempate aleatório:** quando múltiplos nós têm a mesma distância mínima, um é escolhido aleatoriamente — adiciona variedade comportamental ao Ladrão.

**Reconstrução do caminho (`buildPathFromNodes`):**
1. Encontra o nó destino na árvore
2. Percorre a cadeia de `node.root` até chegar à origem
3. Inverte a lista → caminho correto origem → destino

---

## 🧭 Estratégia de Exploração

Quando nenhum Poupador é detectado, o Ladrão usa:

### `getFarthestUnknownNode()`
1. Itera sobre todos os vértices do grafo global
2. Filtra apenas tiles com valor `-2` (desconhecidos)
3. Calcula distância Manhattan de cada um
4. Seleciona do **top 50% mais distantes** aleatoriamente
5. Fallback: `getFarthestKnownNode()` se não há desconhecidos

### `getFarthestKnownNode()`
1. Itera sobre todos os vértices
2. Filtra tiles com valor `0` (conhecidos e caminháveis)
3. Retorna um aleatório da lista
4. Fallback: `"8:8"` (posição do banco)

---

## ⏱️ Sistema de Cooldown de Roubo

Armazenado em: `HashMap<Integer, Integer> targetRefreshRate`

```
{ 100 → 0,   // Poupador 100: pronto para roubar
  110 → 47 } // Poupador 110: ainda em cooldown (47 turnos restantes)
```

**Ativação:** quando `previousMoneyOnHold != getCurrentMoney()` (roubo detectado)

**Cálculo da penalidade:**
```
stoleCoins = getCurrentMoney() * 10
maxRefreshRate = random(100..150)
cooldown = min(stoleCoins, maxRefreshRate)
```

**Decremento:** `updateTargetCooldown()` reduz em 1 por turno.

---

## 📦 Listas e Estruturas de Dados — Resumo Completo

| Estrutura | Classe | Tipo Java | Propósito |
|---|---|---|---|
| `knownField` | Ladrao | `int[30][30]` | Mapa mental do labirinto |
| `nonVisitableLands` | Ladrao | `ArrayList<Integer>` | Terrenos que bloqueiam passagem |
| `targetRefreshRate` | Ladrao | `HashMap<Integer,Integer>` | Cooldown por Poupador |
| `vertexes` | Graph | `HashMap<String, HashMap<String,Integer>>` | Adjacência do grafo |
| `openSet` | A* (local) | `HashMap<String,Integer>` | Candidatos na busca |
| `closedSet` | A* (local) | `ArrayList<String>` | Visitados na busca |
| `path` | A* (local) | `ArrayList<Node>` | Árvore de busca |
| `knownVerticesDistances` | getFarthestKnownNode | `HashMap<String,Integer>` | Candidatos de exploração |
| `unknownVerticesDistances` | getFarthestUnknownNode | `HashMap<String,Integer>` | Candidatos de exploração |
| `vertexesWithMinDistance` | A* (local) | `ArrayList<String>` | Desempate na escolha de nó |

---

## 🚦 Códigos de Direção

| Código | Movimento |
|---|---|
| `0` | Parado |
| `1` | Cima |
| `2` | Direita |
| `3` | Baixo |
| `4` | Esquerda |

---

## ⚠️ Bugs e Limitações Identificados

1. **`(int) Math.random() * 5`** no fallback de `moveUsingAStar()` sempre retorna `0` — a conversão de tipo tem precedência errada. O correto seria `(int)(Math.random() * 5)`.

2. **Comparação `==` entre Strings** em `findPathAStar()`:
   ```java
   if (node.label == current)  // ❌ compara referência
   if (node.label.equals(current))  // ✅ compara valor
   ```
   Pode causar falha na construção da árvore de busca em alguns cenários.

3. **Cooldown com cálculo incorreto:**
   ```java
   int maxRefreshRate = (int) Math.random() * (150 - 100) + 100;
   // Sempre resulta em 100 (mesmo bug do item 1)
   ```

4. **`getAdjacentWalkableFromMemory`** usa direções em ordem reversa (4→1) sem documentação clara do motivo — pode causar inconsistência em casos edge.

---

## 🔁 Ciclo de Vida por Turno

```
Turno N:
  1. Labirinto chama acao()
  2. Visão 5×5 é lida e gravada em knownField
  3. Cooldowns decrementados
  4. Scores calculados para: perseguir / rastrear / explorar
  5. Roulette wheel seleciona ação
  6. A* planeja um passo
  7. Código de direção (1-4) retornado ao simulador
  8. Simulador move o Ladrão
```

---

## 📋 Dependências

- `algoritmo.ProgramaLadrao` — classe base do simulador (não incluída)
- `sensor` — objeto injetado pelo simulador com métodos:
  - `sensor.getVisaoIdentificacao()` → visão 5×5
  - `sensor.getAmbienteOlfatoPoupador()` → olfato 3×3
  - `sensor.getNumeroDeMoedas()` → saldo de moedas
  - `sensor.getPosicao()` → posição atual (`java.awt.Point`)