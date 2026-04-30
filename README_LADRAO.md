# Agente Inteligente: Ladrão (Mundo do Poupador)

Este projeto implementa a inteligência artificial de um agente "Ladrão" para o framework do jogo **Mundo do Poupador**. O objetivo do agente é maximizar a coleta de moedas através da perseguição e roubo dos agentes "Poupadores", utilizando navegação autônoma e estratégias de caça em equipe.

A arquitetura escolhida para este agente é a de um **Agente Reativo com Modelo de Mundo (Model-Based Reflex Agent)**, o que significa que ele não apenas reage ao que vê no momento, mas mantém uma memória persistente do ambiente para tomar decisões de longo prazo.

---

## 1. Autonomia e o "Mapa Mental" (Modelo de Mundo)

Um dos principais requisitos do projeto é que o agente **não possua conhecimento global prévio do labirinto**. Ele nasce "cego" (Fog of War) e deve aprender sobre o ambiente.

Isso foi modelado através da matriz `knownField` (30x30). 
* **O Início:** A matriz é inicializada inteiramente com o valor `-2` (Desconhecido).
* **O Aprendizado:** A cada turno, a função `updateMemoryWithVision()` é chamada. Ela pega o raio de visão local (5x5) e "pinta" os obstáculos, paredes (1) e terrenos livres (0) na matriz.
* **A Persistência:** A memória nunca é apagada. Se o Ladrão virar de costas para uma parede, ele não a vê mais no sensor atual, mas seu "Mapa Mental" a mantém registrada, garantindo que ele não tente atravessá-la no futuro.

---

## 2. A Importância dos Sensores

O Ladrão não trapaceia; ele toma decisões baseadas exclusivamente em estímulos (percepções) recebidos do ambiente:

* **Sensor de Visão (5x5):** É o sensor principal. Informa onde estão as paredes e identifica a presença física do Poupador (`100` ou `110`). É usado para atualizar a memória e engatilhar a ação de **Perseguição**.
* **Sensor de Olfato (3x3):** Uma heurística sensorial poderosa. Poupadores deixam rastros (feromônios) por onde passam. Se o Ladrão não vê o alvo, mas detecta um valor `> 0` no olfato, ele ativa a ação de **Rastreio** (como um cão de caça), seguindo a trilha do menor para o maior valor até obter contato visual.
* **Sensor de Posição & Moedas:** Usados para calcular a matemática de rotas e para validar se um assalto foi bem-sucedido (comparando o saldo anterior com o atual).

---

## 3. Lógica de Decisão: Heurísticas e a "Roleta Viciada"

O Ladrão não segue uma árvore de decisão rígida (if/else estrito). Ele utiliza um sistema de **Pesos Dinâmicos (Scoring)** e **Seleção Probabilística (Roulette Wheel)**. Isso garante que o agente seja imprevisível, orgânico e consiga sair de loops infinitos.

A função `calculateActionScores` avalia o ambiente e distribui notas para três ações possíveis:
1. **Perseguir:** Peso `100.0` (Prioridade Máxima) se o Poupador estiver visível e muito perto.
2. **Rastrear:** Peso `30.0` se houver cheiro forte, mas sem contato visual.
3. **Explorar:** Peso `20.0` se não houver alvos nem cheiros, forçando o Ladrão a mapear áreas novas (`-2`).

### A Matemática da Roleta
Os scores não são ordens, são probabilidades. Eles passam pela função `normalize()`, que transforma os pesos em uma roleta de 100%.
**Fórmula de Normalização:** $P(Ação) = \frac{Score\_Ação}{Total\_Scores}$

*Exemplo:* Mesmo que "Perseguir" tenha 90% de chance, existe uma minúscula chance (10%) do agente decidir "Explorar", o que adiciona ruído comportamental e evita que ele fique permanentemente travado em quinas tentando pegar um Poupador inalcançável.

---

## 4. Comportamentos Modelados (O Cérebro da Caçada)

O método `decideNextActionTarget()` orquestra o comportamento. Foram modelados fenômenos sociais específicos para melhorar a eficiência da equipe de Ladrões:

### O Alvo Mais Próximo
O Ladrão calcula a distância de todos os Poupadores visíveis usando a heurística de **Distância de Manhattan**:  
$D = |x_1 - x_2| + |y_1 - y_2|$  
Ele sempre foca no alvo mais perto, otimizando o tempo de chegada.

### Repulsão Social e "Tática de Cerco" (Flocking/Pincer)
Para evitar o **Efeito Manada** (onde 4 ladrões seguem o mesmo Poupador e deixam o outro livre), foi implementada a função `countThievesAroundTarget()`.
* O Ladrão olha para o Poupador e escaneia um raio de 2 casas ao redor dele.
* Se já existirem **2 ou mais ladrões aliados** fechando o cerco, este Ladrão **ignora** aquele Poupador e ativa a "Exploração" para caçar no resto do mapa.
* Isso permite que a IA crie **Duplas de Caça** dinamicamente: um ladrão ataca pela frente, o outro (via A*) dá a volta para encurralar, e os outros dois ladrões se espalham pelo mapa em busca do segundo Poupador.

---

## 5. Navegação e Movimentação: O Algoritmo A* (A-Estrela)

Decidir *o que* fazer é trabalho da Roleta. Saber *como* chegar lá é trabalho do **Algoritmo A***.

O A* é construído dinamicamente a cada turno.
* Para caçar, ele constrói um Grafo apenas da visão (5x5).
* Para explorar, ele constrói um Grafo gigante de toda a memória conhecida (30x30).

A cada turno, o A* avalia os nós vizinhos usando a função clássica de custo:  
$f(n) = g(n) + h(n)$
* **$g(n)$**: O custo real (quantidade de passos) do Ladrão até o nó atual.
* **$h(n)$**: A heurística (Distância de Manhattan) do nó atual até o destino final.

O A* permite que o Ladrão contorne paredes longas, saia de corredores sem saída ("U-shapes") e trace a rota perfeita. Contudo, como o ambiente é dinâmico (pessoas se movem), o Grafo é destruído e recalculado a cada passo. Assim, se uma parede ou um aliado fechar o caminho no meio da rota, o Ladrão desvia perfeitamente no turno seguinte.

---

## Conclusão

Através da combinação de um **Mapa Mental contínuo**, **Pesos Probabilísticos** e do poderoso algoritmo de busca **A***, o Agente Ladrão demonstra um comportamento altamente autônomo, implacável na perseguição e capaz de cooperar passivamente com seus aliados através da repulsão social, resolvendo o problema do labirinto sem nenhuma informação privilegiada pré-programada.