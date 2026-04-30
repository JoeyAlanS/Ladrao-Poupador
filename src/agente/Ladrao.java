package agente;

import algoritmo.ProgramaLadrao;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Heurística Admissível para otimização da busca A*.
 * Utiliza a geometria "City Block" (Distância de Manhattan) dado que as restrições
 * do ambiente limitam a movimentação a 4 eixos ortogonais, garantindo que
 * h(n) <= custo_real(n).
 */
class HScore {
    public static int calculateManhattanDistance(int[] originCoordinates, int[] destinyCoordinates) {
        return Math.abs(originCoordinates[0] - destinyCoordinates[0])
                + Math.abs(originCoordinates[1] - destinyCoordinates[1]);
    }
}

/**
 * Representação estática de um nó dentro do espaço de estados da árvore de busca.
 * Mantém o encadeamento estrutural (root) necessário para a reconstrução 
 * do trajeto ótimo (backtracking) da fronteira até a raiz após a convergência do algoritmo.
 */
class Node {
    public String label;
    public Node root;

    Node(String label, Node root) {
        this.label = label;
        this.root = root;
    }
}

/**
 * Estrutura topológica do ambiente mapeado.
 * Implementa um Grafo direcionado através de Lista de Adjacências que traduz a 
 * representação de grade discreta (grid) para um espaço de busca navegável.
 */
class Graph {
    protected Map<String, Map<String, Integer>> vertexes = new HashMap<>();

    public void addVertex(String label) {
        this.vertexes.putIfAbsent(label, new HashMap<>());
    }

    public void addEdge(String originLabel, String destinyLabel, int direction) {
        this.vertexes.get(originLabel).put(destinyLabel, direction);
    }

    public Map<String, Integer> getNeighbors(String label) {
        return this.vertexes.get(label);
    }

    public int[] convertLabelToCoordinates(String label) {
        String[] coordinates = label.split(":");
        return new int[]{
                Integer.parseInt(coordinates[0]),
                Integer.parseInt(coordinates[1])
        };
    }

    public String convertCoordinatesToLabel(int[] coordinates) {
        return coordinates[0] + ":" + coordinates[1];
    }

    /**
     * Algoritmo de reconstrução do plano de navegação.
     */
    public ArrayList<String> buildPathFromNodes(ArrayList<Node> path, String destinyLabel) {
        ArrayList<String> reconstructedPath = new ArrayList<>();
        Node destiny = null;
        for (Node node : path) {
            if (node.label.equals(destinyLabel)) {
                destiny = node;
                while (destiny != null) {
                    reconstructedPath.add(destiny.label);
                    destiny = destiny.root;
                }
                break;
            }
        }
        Collections.reverse(reconstructedPath);
        return reconstructedPath;
    }

    /**
     * Algoritmo de Busca Heurística Informada (A*).
     * Avalia o custo ótimo iterativo através da função f(n) = g(n) + h(n).
     * Garante completude e otimalidade no cálculo da rota dentro do modelo de mundo conhecido.
     */
    public ArrayList<String> findPathAStar(String origin, String destiny) {
        if (origin.equals(destiny) || this.getNeighbors(destiny) == null) {
            return null;
        }

        int[] destinyCoordinates = this.convertLabelToCoordinates(destiny);
        Map<String, Integer> gScore = new HashMap<>(); // Custo percorrido g(n)
        gScore.put(origin, 0);

        Map<String, Integer> openSet = new HashMap<>(); // Conjunto de fronteira priorizado f(n)
        int initialH = HScore.calculateManhattanDistance(this.convertLabelToCoordinates(origin), destinyCoordinates);
        openSet.put(origin, initialH);

        ArrayList<String> closedSet = new ArrayList<>(); // Conjunto de nós expandidos
        ArrayList<Node> path = new ArrayList<>();
        path.add(new Node(origin, null));

        while (!openSet.isEmpty()) {
            int minFScore = Collections.min(openSet.values());
            ArrayList<String> candidates = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : openSet.entrySet()) {
                if (entry.getValue() == minFScore) {
                    candidates.add(entry.getKey());
                }
            }

            // Política de desempate estrita (First-In) para inibir thrashing no planejamento de rotas
            String current = candidates.get(0);

            if (current.equals(destiny)) {
                return this.buildPathFromNodes(path, destiny);
            }

            openSet.remove(current);
            closedSet.add(current);

            int currentGScore = gScore.getOrDefault(current, Integer.MAX_VALUE);
            Node currentNodeObj = null;
            for (Node n : path) {
                if (n.label.equals(current)) {
                    currentNodeObj = n;
                    break;
                }
            }

            Map<String, Integer> neighbors = this.getNeighbors(current);
            if (neighbors != null) {
                for (String neighbor : neighbors.keySet()) {
                    if (closedSet.contains(neighbor)) {
                        continue;
                    }

                    int tentativeGScore = currentGScore + 1;
                    if (tentativeGScore < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                        gScore.put(neighbor, tentativeGScore);
                        int[] neighborCoordinates = this.convertLabelToCoordinates(neighbor);
                        int hScore = HScore.calculateManhattanDistance(neighborCoordinates, destinyCoordinates);
                        int fScore = tentativeGScore + hScore;

                        openSet.put(neighbor, fScore);
                        path.removeIf(node -> node.label.equals(neighbor));
                        path.add(new Node(neighbor, currentNodeObj));
                    }
                }
            }
        }
        return null;
    }
}

/**
 * Agente Autônomo da classe Ladrão.
 * Implementa a arquitetura de um "Agente Reativo Baseado em Modelo", lidando
 * ativamente com observabilidade parcial através da construção e manutenção de um
 * Belief State (Estado de Crença) do labirinto.
 */
public class Ladrao extends ProgramaLadrao {
    
    // Belief State: Matriz persistente que modela o conhecimento acumulado do ambiente.
    protected int[][] knownField;
    
    // Espaço de transição efêmero recriado a cada deliberação para o A*.
    protected Graph graph;
    
    // Fronteira de exploração focada (Frontier-based exploration).
    private String explorationObjectiveLocation;
    
    // Controle temporal autônomo (Refractory period) sobre os agentes-alvo.
    private Map<Integer, Integer> targetRefreshRate;
    
    // Avaliação de recompensa imediata capturada.
    private int previousMoneyOnHold;

    // Definição axiomática de topologia não transitável.
    protected ArrayList<Integer> nonVisitableLands = new ArrayList<>(
            Arrays.asList(-1, 1, 3, 4, 5, 200, 210, 220, 230)
    );

    /**
     * Inicializa a matriz de representação sob a premissa de Nevoeiro de Guerra
     * (Fog of War). O valor -2 indica estados espaciais ainda não amostrados pelos sensores.
     */
    private void initializeMemoryField() {
        this.knownField = new int[30][30];
        for (int[] field : this.knownField) {
            Arrays.fill(field, -2);
        }
    }

    public Ladrao() {
        this.initializeMemoryField();
        this.targetRefreshRate = new HashMap<>();
        this.targetRefreshRate.put(100, 0);
        this.targetRefreshRate.put(110, 0);
        this.previousMoneyOnHold = this.getCurrentMoney();
    }

    /**
     * Função de Transição de Estado que infere vizinhos ortogonais com base na 
     * matriz de percepção instantânea (FOV limitado).
     */
    private Map<String, Integer> getAdjacentWalkableFromVision(int x, int y) {
        int[] adjacentLandsIndex = new int[]{0, -1, 0, 1, 1, 0, -1, 0};
        Map<String, Integer> adjacentLands = new HashMap<>();
        int landTravelDirection = 1;

        for (int i = 0; i < adjacentLandsIndex.length; i += 2) {
            int adjacentLandX = adjacentLandsIndex[i] + x;
            int adjacentLandY = adjacentLandsIndex[i + 1] + y;

            if (0 <= adjacentLandX && adjacentLandX <= 29 && 0 <= adjacentLandY && adjacentLandY <= 29) {
                if (!this.isTileBlocked(adjacentLandX, adjacentLandY)) {
                    adjacentLands.put(
                            this.graph.convertCoordinatesToLabel(new int[]{adjacentLandX, adjacentLandY}),
                            landTravelDirection);
                }
            }
            landTravelDirection++;
        }
        return adjacentLands;
    }

    /**
     * Função de Transição de Estado que infere vizinhos ortogonais a partir
     * do estado global deduzido pelo modelo de mundo mantido na memória.
     */
    private Map<String, Integer> getAdjacentWalkableFromMemory(int x, int y) {
        int[] adjacentLandsIndex = new int[]{0, -1, 0, 1, 1, 0, -1, 0};
        Map<String, Integer> adjacentLands = new HashMap<>();
        int landTravelDirection = 1;

        for (int i = 0; i < adjacentLandsIndex.length; i += 2) {
            int adjacentLandX = adjacentLandsIndex[i] + x;
            int adjacentLandY = adjacentLandsIndex[i + 1] + y;

            if (0 <= adjacentLandX && adjacentLandX <= 29 && 0 <= adjacentLandY && adjacentLandY <= 29) {
                if (!this.isTileBlocked(adjacentLandX, adjacentLandY)) {
                    adjacentLands.put(
                            this.graph.convertCoordinatesToLabel(new int[]{adjacentLandX, adjacentLandY}),
                            landTravelDirection);
                }
            }
            landTravelDirection++;
        }
        return adjacentLands;
    }

    /**
     * Reconstrução macroscópica da topologia. Mapeia a totalidade da 
     * memória episódica na estrutura de grafos permitindo busca A* em escopo global.
     */
    private void buildGraphFromMemory() {
        this.graph = new Graph();
        for (int y = 0; y <= 29; y++) {
            for (int x = 0; x <= 29; x++) {
                if (!this.isTileBlocked(x, y)) {
                    String currentLand = this.graph.convertCoordinatesToLabel(new int[]{x, y});
                    this.graph.addVertex(currentLand);

                    for (Map.Entry<String, Integer> entry : this.getAdjacentWalkableFromMemory(x, y).entrySet()) {
                        String adjVertex = entry.getKey();
                        int adjVertexDirection = entry.getValue();
                        this.graph.addVertex(adjVertex);
                        this.graph.addEdge(currentLand, adjVertex, adjVertexDirection);
                    }
                }
            }
        }
    }

    /**
     * Reconstrução microscópica da topologia. Restringe a malha transitável 
     * estritamente aos limites do limiar perceptivo (5x5) para manobras táticas curtas.
     */
    private void buildGraphFromVision() {
        this.graph = new Graph();
        int[] positions = this.getCurrentPosition();
        int thiefX = positions[0];
        int thiefY = positions[1];

        for (int y = thiefY - 2; y <= thiefY + 2; y++) {
            for (int x = thiefX - 2; x <= thiefX + 2; x++) {
                if (0 <= x && x <= 29 && 0 <= y && y <= 29) {
                    if (!this.isTileBlocked(x, y)) {
                        String currentLand = this.graph.convertCoordinatesToLabel(new int[]{x, y});
                        this.graph.addVertex(currentLand);

                        for (Map.Entry<String, Integer> entry : this.getAdjacentWalkableFromVision(x, y).entrySet()) {
                            String adjVertex = entry.getKey();
                            int adjVertexDirection = entry.getValue();
                            this.graph.addVertex(adjVertex);
                            this.graph.addEdge(currentLand, adjVertex, adjVertexDirection);
                        }
                    }
                }
            }
        }
    }

    protected boolean isTargetDetectedBySmell() {
        return Arrays.stream(this.getSmellSensor()).anyMatch(i -> i != 0);
    }

    protected boolean isTargetVisible() {
        return Arrays.stream(this.getCurrentVision()).anyMatch(i -> i == 100 || i == 110);
    }

    /**
     * Encapsula a modelagem utilitária das intenções do agente, processando 
     * parâmetros brutos para uma distribuição de probabilidade.
     */
    private class ActionScores {
        double perseguir;
        double rastrear;
        double explorar;

        ActionScores(double perseguir, double rastrear, double explorar) {
            this.perseguir = perseguir;
            this.rastrear = rastrear;
            this.explorar = explorar;
        }

        /**
         * Normaliza o score vetorial, gerando uma distribuição probabilística convergente em 1.0.
         */
        double[] normalize() {
            double total = perseguir + rastrear + explorar;
            if (total == 0) {
                return new double[]{0.1, 0.1, 0.8}; // Fallback: Induz a expansão de Fronteira
            }
            return new double[]{
                    perseguir / total,
                    rastrear / total,
                    explorar / total
            };
        }
    }

    /**
     * Função Heurística Comportamental. Afeta pesos estáticos sob viéses situacionais,
     * priorizando o estado persecutório e calibrando a sensibilidade olfativa.
     */
    private ActionScores calculateActionScores(int[] visibleTarget, boolean hasSmell, boolean hasUnknownArea) {
        double scorePerseguir = 0.0;
        double scoreRastrear = 0.0;
        double scoreExplorar = 0.0;

        if (visibleTarget != null) {
            int distanceToTarget = HScore.calculateManhattanDistance(this.getCurrentPosition(), visibleTarget);
            scorePerseguir = distanceToTarget <= 2 ? 100.0 : 50.0; 
        }

        if (hasSmell && visibleTarget == null) {
            scoreRastrear = 30.0; 
        } else if (hasSmell) {
            scoreRastrear = 2.0; 
        }

        if (visibleTarget == null && !hasSmell) {
            scoreExplorar = 20.0;
        } else if (hasUnknownArea) {
            scoreExplorar = 1.0; 
        }

        return new ActionScores(scorePerseguir, scoreRastrear, scoreExplorar);
    }

    /**
     * Mecanismo Estocástico (Proportional Roulette Wheel Selection).
     * Interpola as opções baseando-se na densidade de probabilidade para mitigar determinismo.
     */
    private int rouletteWheelSelection(double actionRoll, double[] probabilities) {
        if (actionRoll < probabilities[0]) {
            return 0; // Estado Deliberativo: Perseguir
        } else if (actionRoll < probabilities[0] + probabilities[1]) {
            return 1; // Estado Deliberativo: Rastrear
        } else {
            return 2; // Estado Deliberativo: Explorar
        }
    }

    /**
     * Heurística Social (Flocking Control / Pincer Movement).
     * Quantifica a densidade populacional cooperativa num sub-grid (r=2) do alvo
     * para instigar o comportamento de repulsão local e evitar super-concentração (Efeito Manada).
     */
    private int countThievesAroundTarget(int targetX, int targetY) {
        int count = 0;
        int[] pos = this.getCurrentPosition();
        
        for (int y = targetY - 2; y <= targetY + 2; y++) {
            for (int x = targetX - 2; x <= targetX + 2; x++) {
                if (0 <= x && x <= 29 && 0 <= y && y <= 29) {
                    if (x == pos[0] && y == pos[1]) continue; 
                    
                    int cell = this.knownField[y][x];
                    if (cell == 200 || cell == 210 || cell == 220 || cell == 230) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Pipeline Principal de Tomada de Decisão (Inference Engine).
     * Sintetiza variáveis de estado macro e microscópicas para derivar o alvo ótimo do turno.
     */
    protected int decideNextActionTarget() {
        int[] positions = this.getCurrentPosition();
        int thiefX = positions[0];
        int thiefY = positions[1];

        Random actionSelector = new Random();
        double actionRoll = actionSelector.nextDouble();

        int[] visibleTarget = null;
        int shortestDistance = Integer.MAX_VALUE;
        boolean sawBusySaver = false; 

        // Avaliação de Proximidade (Minimax Simplificado Local)
        if (this.isTargetVisible()) {
            for (int y = thiefY - 2; y <= thiefY + 2; y++) {
                for (int x = thiefX - 2; x <= thiefX + 2; x++) {
                    if (0 <= x && x <= 29 && 0 <= y && y <= 29) {
                        int cellValue = this.knownField[y][x];
                        
                        if ((cellValue == 100 && this.targetRefreshRate.getOrDefault(100, 0) == 0) ||
                            (cellValue == 110 && this.targetRefreshRate.getOrDefault(110, 0) == 0)) {
                            
                            int dist = HScore.calculateManhattanDistance(positions, new int[]{x, y});
                            int aliadosPerto = this.countThievesAroundTarget(x, y);
                            
                            // Gatilho de Descentralização: Ignora o Poupador se o cerco cooperativo (dupla) for avaliado.
                            if (aliadosPerto >= 2) {
                                sawBusySaver = true;
                                continue; 
                            }

                            if (dist < shortestDistance) {
                                shortestDistance = dist;
                                visibleTarget = new int[]{x, y};
                            }
                        }
                    }
                }
            }
        }

        boolean hasSmell = this.isTargetDetectedBySmell() && !sawBusySaver;
        boolean hasUnknownArea = this.hasUnknownAreasNearby();

        ActionScores scores = this.calculateActionScores(visibleTarget, hasSmell, hasUnknownArea);
        double[] probabilities = scores.normalize();
        int chosenAction = this.rouletteWheelSelection(actionRoll, probabilities);

        // Sub-Rotinas Executoras do Vetor de Atuação
        switch (chosenAction) {
            case 0:
                if (visibleTarget != null) return this.chaseTarget(visibleTarget);
                if (hasSmell) return this.trackBySmell(thiefX, thiefY);
                return this.exploreEnvironment();
            case 1:
                if (hasSmell) return this.trackBySmell(thiefX, thiefY);
                if (visibleTarget != null) return this.chaseTarget(visibleTarget);
                return this.exploreEnvironment();
            default:
                return this.exploreEnvironment();
        }
    }

    /**
     * Avaliador de Gradiente Olfativo. Realiza um Greedy Search no campo de 3x3 
     * buscando o menor delta direcional com o alvo oculto.
     */
    private int trackBySmell(int thiefX, int thiefY) {
        int[] saverSmell = this.getSmellSensor();
        int saverSmellIndex = 0;
        int minSaverSmell = Integer.MAX_VALUE;
        int[] minSmellPosition = null;

        for (int y = thiefY - 1; y <= thiefY + 1; y++) {
            for (int x = thiefX - 1; x <= thiefX + 1; x++) {
                if (!(x == thiefX && y == thiefY)) {
                    if (0 <= x && x <= 29 && 0 <= y && y <= 29) {
                        if (saverSmell[saverSmellIndex] <= minSaverSmell && !(saverSmell[saverSmellIndex] <= 0)) {
                            minSaverSmell = saverSmell[saverSmellIndex];
                            minSmellPosition = new int[]{x, y};
                        }
                    }
                    saverSmellIndex++;
                }
            }
        }
        return minSmellPosition != null ? this.chaseTarget(minSmellPosition) : this.exploreEnvironment();
    }

    private boolean hasUnknownAreasNearby() {
        int[] positions = this.getCurrentPosition();
        int thiefX = positions[0];
        int thiefY = positions[1];

        int searchRadius = 10;
        for (int y = Math.max(0, thiefY - searchRadius); y <= Math.min(29, thiefY + searchRadius); y++) {
            for (int x = Math.max(0, thiefX - searchRadius); x <= Math.min(29, thiefX + searchRadius); x++) {
                if (this.isTileUnknown(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Avaliação matricial utilizando mapeamento em tempo contínuo [linha][coluna].
    protected boolean isTileBlocked(int x, int y) {
        return nonVisitableLands.contains(this.knownField[y][x]);
    }

    protected boolean isTileUnknown(int x, int y) {
        return this.knownField[y][x] == -2;
    }

    protected boolean isTileKnown(int x, int y) {
        return this.knownField[y][x] == 0;
    }

    protected boolean hasReachedExplorationGoal() {
        return this.graph.convertCoordinatesToLabel(this.getCurrentPosition()).equals(this.explorationObjectiveLocation);
    }

    protected int[] getCurrentVision() {
        return this.sensor.getVisaoIdentificacao();
    }

    protected int getCurrentMoney() {
        return this.sensor.getNumeroDeMoedas();
    }

    protected int[] getSmellSensor() {
        return this.sensor.getAmbienteOlfatoPoupador();
    }

    protected int[] getCurrentPosition() {
        java.awt.Point currentPosition = this.sensor.getPosicao();
        int x = (int) currentPosition.getX();
        int y = (int) currentPosition.getY();
        return new int[]{x, y};
    }

    /**
     * Controlador de penalidades autonômico para prevenir colapso simulado do jogo (Spamming de moedas).
     */
    private void updateTargetCooldown() {
        for (Integer saverId : this.targetRefreshRate.keySet()) {
            if (this.targetRefreshRate.get(saverId) > 0) {
                this.targetRefreshRate.put(saverId, this.targetRefreshRate.get(saverId) - 1);
            }
        }
    }

    /**
     * Módulo de Belief Update. Intersecciona os novos vetores parciais capturados com o
     * modelo de memória profundo, aplicando sobreposição construtiva (mantendo estruturas já consolidadas).
     */
    private void updateMemoryWithVision() {
        int[] positions = this.getCurrentPosition();
        int thiefX = positions[0];
        int thiefY = positions[1];
        int[] currentView = this.getCurrentVision();
        int gridViewIndex = 0;

        for (int y = thiefY - 2; y <= thiefY + 2; y++) {
            for (int x = thiefX - 2; x <= thiefX + 2; x++) {
                if (!(x == thiefX && y == thiefY)) {
                    if (0 <= x && x <= 29 && 0 <= y && y <= 29) {
                        if ((currentView[gridViewIndex] != -2 && this.knownField[y][x] == -2) ||
                                (currentView[gridViewIndex] != -2 && this.knownField[y][x] != -2)) {
                            this.knownField[y][x] = currentView[gridViewIndex];
                        }
                    }
                    gridViewIndex++;
                } else {
                    this.knownField[y][x] = 0;
                }
            }
        }
    }

    /**
     * Abstração vetorial que direciona a atuação (moveTarget). Resolve estocasticamente 
     * limites computacionais ativando períodos refratários pós-roubo.
     */
    private int chaseTarget(int[] targetLocation) {
        this.buildGraphFromVision();

        if (this.previousMoneyOnHold != this.getCurrentMoney()) {
            int maxRefreshRate = (int) (Math.random() * 50) + 100;
            int stoleCoins = this.getCurrentMoney() * 10;

            if (stoleCoins == 0) {
                stoleCoins = (int) (Math.random() * 20) + 10;
            }

            this.targetRefreshRate.put(
                    this.knownField[targetLocation[1]][targetLocation[0]],
                    Math.max(stoleCoins, maxRefreshRate)
            );
            this.previousMoneyOnHold = this.getCurrentMoney();
        }

        return this.moveUsingAStar(
                this.graph.convertCoordinatesToLabel(this.getCurrentPosition()),
                this.graph.convertCoordinatesToLabel(targetLocation)
        );
    }

    /**
     * Heurística de Fallback Topológico (Safe Point Analysis).
     */
    protected String getFarthestKnownNode() {
        Map<String, Integer> knownVerticesDistances = new HashMap<>();
        Random landSelector = new Random();
        int[] currentThiefPosition = this.getCurrentPosition();

        for (String vertex : this.graph.vertexes.keySet()) {
            int[] vertexCoordinates = this.graph.convertLabelToCoordinates(vertex);
            if (this.isTileKnown(vertexCoordinates[0], vertexCoordinates[1])) {
                knownVerticesDistances.put(vertex,
                        HScore.calculateManhattanDistance(currentThiefPosition, vertexCoordinates));
            }
        }

        ArrayList<String> vertexes = new ArrayList<>(knownVerticesDistances.keySet());
        if (!vertexes.isEmpty()) {
            return vertexes.get(landSelector.nextInt(vertexes.size()));
        } else if (!knownVerticesDistances.isEmpty()) {
            return Collections.max(knownVerticesDistances.entrySet(), Map.Entry.comparingByValue()).getKey();
        } else {
            return "8:8"; 
        }
    }

    /**
     * Estratégia de Frontier-Based Exploration para o mapeamento exaustivo do ambiente.
     */
    protected String getFarthestUnknownNode() {
        Map<String, Integer> unknownVerticesDistances = new HashMap<>();
        Random landSelector = new Random();
        int[] currentThiefPosition = this.getCurrentPosition();

        for (String vertex : this.graph.vertexes.keySet()) {
            int[] vertexCoordinates = this.graph.convertLabelToCoordinates(vertex);
            if (this.isTileUnknown(vertexCoordinates[0], vertexCoordinates[1])) {
                unknownVerticesDistances.put(
                        vertex,
                        HScore.calculateManhattanDistance(currentThiefPosition, vertexCoordinates));
            }
        }

        if (!unknownVerticesDistances.isEmpty()) {
            int maxDistance = (int) Math.floor(Collections.max(unknownVerticesDistances.entrySet(), Map.Entry.comparingByValue()).getValue() / 2.0);
            ArrayList<String> unknownVerticesMaxDistance = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : unknownVerticesDistances.entrySet()) {
                if (entry.getValue() >= maxDistance) {
                    unknownVerticesMaxDistance.add(entry.getKey());
                }
            }
            if (!unknownVerticesMaxDistance.isEmpty()) {
                return unknownVerticesMaxDistance.get(landSelector.nextInt(unknownVerticesMaxDistance.size()));
            }
        }
        return this.getFarthestKnownNode();
    }

    /**
     * Roteador Global que articula as travessias estruturais a longo prazo baseadas na
     * modelagem deduzida do State Space contínuo.
     */
    private int exploreEnvironment() {
        this.buildGraphFromMemory();
        if (this.explorationObjectiveLocation == null || this.hasReachedExplorationGoal()) {
            this.explorationObjectiveLocation = this.getFarthestUnknownNode();
        }
        int[] thiefPosition = this.getCurrentPosition();
        return this.moveUsingAStar(
                this.graph.convertCoordinatesToLabel(new int[]{thiefPosition[0], thiefPosition[1]}),
                this.explorationObjectiveLocation);
    }

    /**
     * Unidade de Atuação (Actuator). Transforma o macro-planejamento guiado 
     * pelo Algoritmo A* em ações vetoriais unitárias no mapa temporal do simulador.
     */
    private int moveUsingAStar(String origin, String destiny) {
        ArrayList<String> path = this.graph.findPathAStar(origin, destiny);
        
        // Verifica a integridade da rota evitando Overlaps com a posição zero.
        if (path != null && path.size() > 1) {
            return this.graph.getNeighbors(path.get(0)).get(path.get(1));
        } 
        
        // Protocolo de Resolução de Bloqueios. Injeta Entropia (movimento estocástico em plano cartesiano)
        // redefinindo compulsoriamente os objetivos não atingíveis pela memória atual.
        this.explorationObjectiveLocation = this.getFarthestUnknownNode();
        return (int) (Math.random() * 4) + 1;
    }

    /**
     * Interface de Simulação: O Loop Perceive-Think-Act do Agente Inteligente.
     */
    @Override
    public int acao() {
        this.updateMemoryWithVision();
        this.updateTargetCooldown();
        return this.decideNextActionTarget();
    }
}