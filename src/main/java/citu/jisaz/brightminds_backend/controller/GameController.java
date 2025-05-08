package citu.jisaz.brightminds_backend.controller;

import citu.jisaz.brightminds_backend.dto.GameDTO;
import citu.jisaz.brightminds_backend.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// No Authentication object needed in method signature if not directly used for userId and principal is not complexly evaluated by SpEL

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    // Library games can be viewed by any authenticated user (teacher or student).
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GameDTO>> getAllLibraryGames() throws ExecutionException, InterruptedException {
        List<GameDTO> games = gameService.getAllLibraryGames();
        return ResponseEntity.ok(games);
    }

    @GetMapping("/{libraryGameId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameDTO> getLibraryGameById(@PathVariable String libraryGameId)
            throws ExecutionException, InterruptedException {
        GameDTO game = gameService.getLibraryGameById(libraryGameId);
        return ResponseEntity.ok(game);
    }
}