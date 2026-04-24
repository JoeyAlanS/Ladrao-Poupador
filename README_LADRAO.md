# Documentação da Classe Ladrão

## Visão Geral

A classe `Ladrão` implementa um agente inteligente que navega autonomamente em um labirinto 30x30, perseguindo e roubando moedas de `Poupadores`. O agente utiliza algoritmos avançados de busca (A*), memória espacial e heurísticas sensoriais para tomar decisões estratégicas.

---

## Características Principais

### Capacidades Sensoriais

| Sensor | Alcance | Descrição |
|--------|---------|-----------|
| **Visão** | 5x5 (2 tiles em cada direção) | Detecta tipos de terreno e agentes visíveis |
| **Olfato** | 3x3 (1 tile em cada direção) | Detecta presença de Poupadores pelo cheiro |
| **Memória** | 30x30 (mapa completo) | Mapa mental do labirinto explorado |

### Estratégia de Comportamento

O Ladrão segue uma **hierarquia de prioridades**:

1. **Perseguição**: Se vê um Poupador → persegue (cooldown é verificado ao executar roubo)
2. **Rastreamento**: Se sente o cheiro de um Poupador → segue o cheiro mais forte
3. **Exploração**: Caso contrário → explora novos territórios

---

## Estrutura de Classes Auxiliares

### Classe `HScore`

```java
public static int calculateManhattanDistance(int[] origin, int[] destiny)
```

Calcula a **distância de Manhattan** entre dois pontos:
- **Fórmula**: |x1 - x2| + |y1 - y2|
- **Uso**: Heurística para otimizar o algoritmo A*
- **Exemplo**: Distância de (0,0) a (3,4) = 7

---

### Classe `Node`

Representa um nó na árvore de busca do A*:

```java
class Node {
    public String label;    // Rótulo no formato "x:y"
    public Node root;       // Referência ao nó pai
}
```

**Uso**: Permite reconstruir o caminho da origem ao destino após a busca.

---

### Classe `Graph`

Implementa um grafo adjacência usado para a busca A*:

```java
protected Map<String, Map<String, Integer>> vertexes
```

**Estrutura**:
- Chave externa: Rótulo do vértice ("x:y")
- Valor: Mapa de vizinhos e direções (1-4)

**Métodos principais**:
- `addVertex(label)`: Adiciona um nó ao grafo
- `addEdge(origin, destiny, direction)`: Conecta dois nós
- `getNeighbors(label)`: Retorna vizinhos de um nó
- `findPathAStar(origin, destiny)`: Busca o caminho mais curto

---

## Sistemas de Valores do Labirinto

Cada célula do mapa mental tem um valor inteiro:

```
-2  = Desconhecido (nunca visto)
-1  = Fora de alcance/invisibilidade
 0  = Espaço vazio (caminável)
 1  = Parede (bloqueado)
 3  = Banco (local especial)
 4  = Moeda (colecionável)
 5  = Pastilha do Poder (item especial)
100, 110 = Poupadores (IDs dos agentes)
200-230  = Ladrões (IDs dos agentes)
```

---

## Métodos Principais

### 1. **Inicialização**

#### `public Ladrao()`
- Inicializa o mapa mental (30x30 com todas as células como desconhecidas)
- Configura cooldowns de roubo para Poupadores (100 e 110)
- Armazena o saldo de moedas anterior

#### `private void initializeMemoryField()`
- Cria matriz 30x30 preenchida com -2 (desconhecido)
- Chamada uma única vez no construtor

---

### 2. **Construção do Grafo**

#### `private void buildGraphFromMemory()`
Constrói grafo da memória completa:
- Percorre toda a matriz 30x30
- Conecta todos os terrenos visitáveis
- **Uso**: Exploração e planejamento de longo prazo
- **Complexidade**: O(n²) onde n=30

#### `private void buildGraphFromVision()`
Constrói grafo da visão local:
- Percorre apenas a área 5x5 visível
- **Uso**: Perseguição em tempo real
- **Velocidade**: Mais rápido que buildGraphFromMemory

#### `private Map<String, Integer> getAdjacentWalkableFromVision(x, y)`
Retorna vizinhos visitáveis na visão:
- Ordena: 1 (cima), 2 (direita), 3 (baixo), 4 (esquerda)
- **Retorna**: Mapa com vizinhos e direções

#### `private Map<String, Integer> getAdjacentWalkableFromMemory(x, y)`
Retorna vizinhos visitáveis na memória:
- Ordena: 4 (esquerda), 3 (baixo), 2 (direita), 1 (cima)
- **Nota**: Ordem invertida para coerência de navegação

---

### 3. **Detecção de Alvo**

#### `protected boolean isTargetVisible()`
Detecta Poupadores na visão direta:
```java
// Procura pelos IDs 100 ou 110 na visão
return Arrays.stream(this.getCurrentVision()).anyMatch(i -> i == 100 || i == 110);
```

#### `protected boolean isTargetDetectedBySmell()`
Detecta Poupadores pelo olfato:
```java
// Se qualquer célula do olfato ≠ 0, há cheiro
return Arrays.stream(this.getSmellSensor()).anyMatch(i -> i != 0);
```

---

### 4. **Checagem de Terreno**

#### `protected boolean isTileBlocked(x, y)`
Verifica se um terreno é intransponível:
```
Bloqueados: -1, 1 (parede), 3 (banco), 4 (moeda), 5 (pastilha), 
            200, 210, 220, 230 (outros ladrões)
```

#### `protected boolean isTileUnknown(x, y)`
Retorna true se célula = -2 (desconhecida)

#### `protected boolean isTileKnown(x, y)`
Retorna true se célula = 0 (caminável conhecido)

#### `protected boolean hasReachedExplorationGoal()`
Verifica se Ladrão chegou ao objetivo de exploração

---

### 5. **Sensores**

#### `protected int[] getCurrentVision()`
Retorna array 5x5 da visão atual

#### `protected int[] getCurrentPosition()`
Retorna [x, y] da posição atual do Ladrão

#### `protected int getCurrentMoney()`
Retorna quantidade de moedas que o Ladrão carrega

#### `protected int[] getSmellSensor()`
Retorna array 3x3 do olfato (intensidade do cheiro)

---

### 6. **Atualização de Estado**

#### `private void updateMemoryWithVision()`
Sincroniza visão com memória:
- Itera sobre a área visível (5x5)
- Atualiza células desconhecidas com informações vistas
- **Importante**: Evita que o Ladrão "esqueça" de terrenos já explorados

#### `private void updateTargetCooldown()`
Decrementa cooldowns de roubo:
```java
// Reduz cooldown de cada Poupador em 1 a cada turno
if (cooldown > 0) cooldown--;
```

---

### 7. **Tomada de Decisão**

#### `protected int decideNextActionTarget()`
Orquestra a IA do Ladrão. Hierarquia:

1. **Visão + Sem Cooldown** → `chaseTarget()`
2. **Cheiro Detectado** → Segue para posição com cheiro mais forte
3. **Padrão** → `exploreEnvironment()`

---

### 8. **Estratégias de Movimento**

#### `private int chaseTarget(int[] targetLocation)`
Persegue um Poupador:
- Constrói grafo da visão com `buildGraphFromVision()`
- Executa A* até a posição do alvo
- Se roubo bem-sucedido: ativa cooldown

**Detecção de roubo**: Comparação de `getCurrentMoney()` com valor anterior

#### `private int exploreEnvironment()`
Explora sistematicamente:
- Constrói grafo da memória com `buildGraphFromMemory()`
- Define objetivo = terreno desconhecido mais distante
- Executa A* até objetivo
- Quando atinge: seleciona novo objetivo

#### `private int moveUsingAStar(origin, destiny)`
Executa uma iteração do A* e retorna primeiro movimento:
- Obtém caminho completo com `graph.findPathAStar()`
- Extrai primeira direção do caminho
- Se caminho falhar: seleciona novo objetivo

---

### 9. **Seleção de Objetivos**

#### `protected String getFarthestKnownNode()`
Encontra terreno conhecido mais distante:
- Calcula distância de Manhattan a todos os conhecidos
- Retorna o mais distante
- **Fallback**: "8:8" (posição do banco)

#### `protected String getFarthestUnknownNode()`
Encontra terreno desconhecido mais distante:
- Identifica desconhecidos no raio > 50% da distância máxima
- Escolhe aleatoriamente entre candidatos
- **Fallback**: Chama `getFarthestKnownNode()`

---

### 10. **Método Principal**

#### `@Override public int acao()`
Executado a cada turno de simulação:

```java
public int acao() {
    // 1. Atualiza mapa com visão
    this.updateMemoryWithVision();
    
    // 2. Decrementa cooldowns
    this.updateTargetCooldown();
    
    // 3. Decide ação e move
    return this.decideNextActionTarget();
}
```

---

## Algoritmo A* Detalhado

O método `findPathAStar(origin, destiny)` implementa o algoritmo A*:

### Estrutura de Dados
- **OpenSet**: Nós a explorar (com distâncias associadas)
- **ClosedSet**: Nós já explorados
- **Path**: Lista de nós visitados (árvore de busca)

### Algoritmo
```
1. Inicializa openSet com origem (distância = 0)
2. Enquanto openSet não vazio:
   a. Encontra nó com menor distância em openSet
   b. Se múltiplos: escolhe aleatoriamente (variedade)
   c. Se é destino: reconstrói e retorna caminho
   d. Move para closedSet
   e. Para cada vizinho não explorado:
      - Calcula distância de Manhattan até destino
      - Adiciona a openSet
   f. Continua
3. Se openSet vazio: retorna null (sem solução)
```

### Complexidade
- **Tempo**: O(n²) no pior caso (labirinto completo)
- **Espaço**: O(n) para estruturas de armazenamento

---

## Fluxo de Execução Completo

```
TURNO DO LADRÃO:
    ↓
updateMemoryWithVision()
    ↓ (atualiza mapa)
updateTargetCooldown()
    ↓ (decrementa cooldowns)
decideNextActionTarget()
    ├─ isTargetVisible() ?
    │   ├─ SIM → chaseTarget()
    │   │         ├─ buildGraphFromVision()
    │   │         ├─ findPathAStar(atual, alvo)
    │   │         └─ Retorna primeiro movimento
    │   │
    │   └─ NÃO → isTargetDetectedBySmell() ?
    │       ├─ SIM → Localiza cheiro mais forte
    │       │         └─ chaseTarget(cheiro)
    │       │
    │       └─ NÃO → exploreEnvironment()
    │               ├─ buildGraphFromMemory()
    │               ├─ Seleciona objetivo (desconhecido distante)
    │               ├─ findPathAStar(atual, objetivo)
    │               └─ Retorna primeiro movimento
    ↓
Retorna código de direção (1-4)
    ↓
Simulador executa movimento
```

---

## Exemplo de Uso

```java
// Criação
Ladrao thief = new Ladrao();

// Execução em loop de simulação
public void simulate() {
    for (int turn = 0; turn < MAX_TURNS; turn++) {
        int move = thief.acao();  // Obtém decisão da IA
        executeMove(thief, move);  // Move o Ladrão
    }
}
```

---

## Notas de Implementação

### Pontos Fortes
- ✅ Algoritmo A* otimizado com heurística adequada
- ✅ Memória espacial que permite aprendizado do labirinto
- ✅ Três modos de detecção (visão, olfato, memória)
- ✅ Cooldown evita behavior explorador após roubo
- ✅ Escolha aleatória entre candidatos iguais (IA natural)

### Limitações
- ⚠️ Recalcula grafo a cada turno (podia ser otimizado)
- ⚠️ Sem previsão de movimento de Poupadores
- ⚠️ Sem colaboração entre Ladrões

### Possíveis Melhorias
1. **Cache de grafo**: Manter grafo e atualizar incrementalmente
2. **Previsão**: Antecipar movimento de Poupadores
3. **Coordenação**: Ladrões comunicarem-se (multi-agente)
4. **Aprendizado**: Reforço via reinforcement learning
5. **Otimização**: Jump-point search em vez de A* puro

---

## Referências

- **Algoritmo A***: Hart, P. E., Nilsson, N. J., & Raphael, B. (1968)
- **Distância Manhattan**: Métrica L₁ em espaços Euclidianos
- **Heurística Admissível**: A* garante otimalidade quando heurística é admissível

---

## Análise de Autonomia

### Este agente (Ladrão) TEM AUTONOMIA COMPLETA

**O Ladrão é um agente autônomo porque:**

| Característica | Ladrão | Observação |
|---|---|---|
| **Memória** | Sim | Matriz 30x30 que persiste e acumula conhecimento |
| **Sensores** |  Sim | Visão 5x5, olfato 3x3, propriocepção |
| **Lógica de Decisão** |  Sim | Hierarquia de prioridades e tomada de decisão contextual |
| **Planejamento** |  Sim | Algoritmo A* para planejamento de caminhos |
| **Aprendizado** |  Sim | Mapeia e se adapta ao labirinto |
| **Independência** |  Sim | Age sem intervenção externa ou scripts pré-programados |
| **Adaptabilidade** |  Sim | Muda estratégia baseado em condições (visão → olfato → exploração) |

### Comparativo com o Poupador

| Aspecto | Ladrão | Poupador |
|---|---|---|
| **Autonomia** |  TOTAL | ❌ NENHUMA |
| **Implementação** | ~1100 linhas de lógica inteligente | 2 linhas: `Math.random() * 5` |
| **Memória** | 30x30 matriz persistente | Nenhuma |
| **Sensores** | 3 sensores avançados | Básicos herdados |
| **Comportamento** | Estratégico e adaptativo | Aleatório puro |

### Conclusão

O **Ladrão é um agente autônomo completo** que:
1. Percebe o ambiente através de múltiplos sensores
2. Mantém modelo mental (memória espacial)
3. Delibera sobre ações (A* + prioridades)
4. Age de forma independente e dinâmica
5. Adapta comportamento a novas situações

Segue os princípios de um **agente inteligente** conforme definido em Inteligência Artificial moderna (Russell & Norvig).

