package at.aau.serg.websocketserver.controller;

import at.aau.serg.websocketserver.TestDataUtil;
import at.aau.serg.websocketserver.demo.websocket.StompFrameHandlerClientImpl;
import at.aau.serg.websocketserver.domain.dto.PlacedTileDto;
import at.aau.serg.websocketserver.domain.dto.GameLobbyDto;
import at.aau.serg.websocketserver.domain.dto.GameSessionDto;
import at.aau.serg.websocketserver.domain.entity.GameSessionEntity;
import at.aau.serg.websocketserver.domain.pojo.GameState;
import at.aau.serg.websocketserver.domain.entity.GameLobbyEntity;
import at.aau.serg.websocketserver.domain.entity.PlayerEntity;
import at.aau.serg.websocketserver.mapper.GameLobbyMapper;
import at.aau.serg.websocketserver.mapper.PlayerMapper;
import at.aau.serg.websocketserver.service.GameLobbyEntityService;
import at.aau.serg.websocketserver.service.GameSessionEntityService;
import at.aau.serg.websocketserver.service.PlayerEntityService;
import at.aau.serg.websocketserver.service.TileDeckEntityService;
import at.aau.serg.websocketserver.statuscode.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GameSessionControllerIntegrationTest {

    private final ObjectMapper objectMapper;
    private final GameLobbyMapper gameLobbyMapper;
    private final PlayerMapper playerMapper;
    private final GameSessionEntityService gameSessionEntityService;
    private final GameLobbyEntityService gameLobbyEntityService;
    private final PlayerEntityService playerEntityService;
    private final TileDeckEntityService tileDeckEntityService;



    @Autowired
    public GameSessionControllerIntegrationTest(ObjectMapper objectMapper, GameLobbyMapper gameLobbyMapper, PlayerMapper playerMapper, GameSessionEntityService gameSessionEntityService, GameLobbyEntityService gameLobbyEntityService, PlayerEntityService playerEntityService, TileDeckEntityService tileDeckEntityService) {
        this.objectMapper = objectMapper;
        this.gameLobbyMapper = gameLobbyMapper;
        this.playerMapper = playerMapper;
        this.gameSessionEntityService = gameSessionEntityService;
        this.gameLobbyEntityService = gameLobbyEntityService;
        this.playerEntityService = playerEntityService;
        this.tileDeckEntityService = tileDeckEntityService;
    }

    @LocalServerPort
    private int port;
    private final String WEBSOCKET_URI = "ws://localhost:%d/websocket-broker";
    BlockingQueue<String> messages;
    BlockingQueue<String> messages2;

    @BeforeEach
    public void setUp() {
        messages = new LinkedBlockingDeque<>();
        messages2 = new LinkedBlockingDeque<>();
    }

    @AfterEach
    public void tearDown() {
        messages = null;
        messages2 = null;
    }

    @Test
    void testThatCreateGameSessionReturnsCreatedGameSessionIdToTopic() throws Exception {
        GameLobbyDto gameLobbyDtoA = TestDataUtil.createTestGameLobbyDtoA();
        GameLobbyEntity gameLobbyEntityA = gameLobbyMapper.mapToEntity(gameLobbyDtoA);

        PlayerEntity playerEntityA = TestDataUtil.createTestPlayerEntityA(null);
        PlayerEntity playerEntityB = TestDataUtil.createTestPlayerEntityB(null);
        PlayerEntity playerEntityC = TestDataUtil.createTestPlayerEntityC(null);
        gameLobbyEntityA.setLobbyAdminId(playerEntityA.getId());

        playerEntityService.createPlayer(playerEntityA);
        playerEntityService.createPlayer(playerEntityB);
        playerEntityService.createPlayer(playerEntityC);

        gameLobbyEntityService.createLobby(gameLobbyEntityA);
        assertThat(gameLobbyEntityService.findById(gameLobbyDtoA.getId())).isPresent();

        playerEntityService.joinLobby(gameLobbyEntityA.getId(), playerEntityA);
        playerEntityService.joinLobby(gameLobbyEntityA.getId(), playerEntityB);
        playerEntityService.joinLobby(gameLobbyEntityA.getId(), playerEntityC);

        GameSessionDto gameSessionDtoA = TestDataUtil.createTestGameSessionDtoA(playerMapper.mapToDto(playerEntityA));
        assertThat(gameSessionEntityService.findById(gameSessionDtoA.getId())).isEmpty();

        StompSession session = initStompSession("/topic/lobby-" + gameLobbyDtoA.getId() + "/game-start", messages);
        session.send("/app/game-start", gameLobbyDtoA.getId() + "");

        String actualResponse = messages.poll(1, TimeUnit.SECONDS);

        assertThat(gameSessionEntityService.findById(gameSessionDtoA.getId())).isPresent();
        assertThat(actualResponse).isEqualTo(gameSessionDtoA.getId() + "");
    }

    @Test
    void testThatCreateGameSessionReturnsUpdatedLobbyListToQueue() throws Exception {
        GameLobbyDto gameLobbyDtoA = TestDataUtil.createTestGameLobbyDtoA();
        GameLobbyEntity gameLobbyEntityA = gameLobbyMapper.mapToEntity(gameLobbyDtoA);
        GameLobbyDto gameLobbyDtoB = TestDataUtil.createTestGameLobbyDtoB();
        GameLobbyEntity gameLobbyEntityB = gameLobbyMapper.mapToEntity(gameLobbyDtoB);

        PlayerEntity playerEntityA = TestDataUtil.createTestPlayerEntityA(null);
        PlayerEntity playerEntityB = TestDataUtil.createTestPlayerEntityB(null);
        PlayerEntity playerEntityC = TestDataUtil.createTestPlayerEntityC(null);
        gameLobbyEntityA.setLobbyAdminId(playerEntityA.getId());
        gameLobbyEntityB.setLobbyAdminId(playerEntityA.getId());

        playerEntityService.createPlayer(playerEntityA);
        playerEntityService.createPlayer(playerEntityB);
        playerEntityService.createPlayer(playerEntityC);

        gameLobbyEntityService.createLobby(gameLobbyEntityA);
        gameLobbyEntityService.createLobby(gameLobbyEntityB);
        assertThat(gameLobbyEntityService.findById(gameLobbyDtoA.getId())).isPresent();
        assertThat(gameLobbyEntityService.findById(gameLobbyDtoB.getId())).isPresent();

        playerEntityService.joinLobby(gameLobbyEntityA.getId(), playerEntityA);
        playerEntityService.joinLobby(gameLobbyEntityA.getId(), playerEntityB);
        playerEntityService.joinLobby(gameLobbyEntityA.getId(), playerEntityC);

        GameSessionDto gameSessionDtoA = TestDataUtil.createTestGameSessionDtoA(playerMapper.mapToDto(playerEntityA));
        assertThat(gameSessionEntityService.findById(gameSessionDtoA.getId())).isEmpty();

        StompSession session = initStompSession("/user/queue/lobby-list-response", messages);
        session.send("/app/game-start", gameLobbyDtoA.getId() + "");

        List<GameLobbyDto> gameLobbyDtoList = new ArrayList<>();
        gameLobbyDtoA.setGameState(GameState.IN_GAME);
        gameLobbyDtoA.setLobbyAdminId(playerEntityA.getId());
        gameLobbyDtoA.setNumPlayers(3);
        gameLobbyDtoList.add(gameLobbyDtoA);

        gameLobbyDtoB.setLobbyAdminId(playerEntityA.getId());
        gameLobbyDtoList.add(gameLobbyDtoB);

        String expectedResponse = objectMapper.writeValueAsString(gameLobbyDtoList);
        String actualResponse = messages.poll(1, TimeUnit.SECONDS);

        assertThat(gameSessionEntityService.findById(gameSessionDtoA.getId())).isPresent();
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    @Test
    void testThatCreateGameSessionWhenGivenInvalidLobbyIdFails() throws Exception {
        GameLobbyDto gameLobbyDtoA = TestDataUtil.createTestGameLobbyDtoA();
        GameLobbyEntity gameLobbyEntityA = gameLobbyMapper.mapToEntity(gameLobbyDtoA);

        PlayerEntity playerEntityA = TestDataUtil.createTestPlayerEntityA(null);
        PlayerEntity playerEntityB = TestDataUtil.createTestPlayerEntityB(null);
        PlayerEntity playerEntityC = TestDataUtil.createTestPlayerEntityC(null);
        gameLobbyEntityA.setLobbyAdminId(playerEntityA.getId());

        playerEntityService.createPlayer(playerEntityA);
        playerEntityService.createPlayer(playerEntityB);
        playerEntityService.createPlayer(playerEntityC);

        assertThat(gameLobbyEntityService.findById(gameLobbyDtoA.getId())).isEmpty();

        GameSessionDto gameSessionDtoA = TestDataUtil.createTestGameSessionDtoA(playerMapper.mapToDto(playerEntityA));
        assertThat(gameSessionEntityService.findById(gameSessionDtoA.getId())).isEmpty();

        StompSession session = initStompSession("/user/queue/errors", messages);
        session.send("/app/game-start", gameLobbyDtoA.getId() + "");

        String expectedResponse = "ERROR: " + ErrorCode.ERROR_1003.getErrorCode();
        String actualResponse = messages.poll(1, TimeUnit.SECONDS);

        assertThat(gameSessionEntityService.findById(gameSessionDtoA.getId())).isEmpty();
        assertThat(actualResponse).isEqualTo(expectedResponse);
    }

    // test forwarding of a placed GameBoardTileDto to all players in the game session
    @Test
    void testThatPlaceTileForwardsPlacedTileDtoToAllPlayers() throws Exception {

        GameLobbyEntity testGameLobbyEntityA = TestDataUtil.createTestGameLobbyEntityA();
        //gameLobbyEntityService.createLobby(testGameLobbyEntityA);

        PlayerEntity testPlayerEntityA = TestDataUtil.createTestPlayerEntityA(testGameLobbyEntityA);
        PlayerEntity testPlayerEntityB = TestDataUtil.createTestPlayerEntityB(testGameLobbyEntityA);
        PlayerEntity testPlayerEntityC = TestDataUtil.createTestPlayerEntityC(testGameLobbyEntityA);

        //playerEntityService.createPlayer(testPlayerEntityA);
        //playerEntityService.createPlayer(testPlayerEntityB);
        //playerEntityService.createPlayer(testPlayerEntityC);

        GameSessionEntity gameSessionEntity = TestDataUtil.createTestGameSessionEntityWith3Players();
        //GameSessionEntity databaseGameSession = gameSessionEntityService.createGameSession(testGameLobbyEntityA.getId());

        StompSession session = initStompSession("/topic/game-session-" + gameSessionEntity.getId() + "/tile", messages);
        StompSession session2 = initStompSession("/topic/game-session-" + gameSessionEntity.getId() + "/tile", messages2);

        PlacedTileDto placedTileDto = TestDataUtil.createTestPlacedTileDto(gameSessionEntity.getId());

        session.send("/app/place-tile", objectMapper.writeValueAsString(placedTileDto));

        String actualResponse = messages.poll(1, TimeUnit.SECONDS);
        String actualResponse2 = messages2.poll(1, TimeUnit.SECONDS);


        assertThat(actualResponse).isEqualTo(objectMapper.writeValueAsString(placedTileDto));
        assertThat(actualResponse2).isEqualTo(objectMapper.writeValueAsString(placedTileDto));
    }

    public StompSession initStompSession(String topic, BlockingQueue<String> messages) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        StompSession session = stompClient.connectAsync(String.format(WEBSOCKET_URI, port),
                        new StompSessionHandlerAdapter() {
                        })
                .get(1, TimeUnit.SECONDS);

        // subscribes to the topic defined in WebSocketBrokerController
        // and adds received messages to WebSocketBrokerIntegrationTest#messages
        session.subscribe(topic, new StompFrameHandlerClientImpl(messages));

        return session;
    }
}
