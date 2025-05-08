package citu.jisaz.brightminds_backend.service;

import citu.jisaz.brightminds_backend.dto.GameDTO;
import citu.jisaz.brightminds_backend.exception.ResourceNotFoundException;
import citu.jisaz.brightminds_backend.model.Game;
import citu.jisaz.brightminds_backend.repository.GameRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public List<GameDTO> getAllLibraryGames() throws ExecutionException, InterruptedException {
        logger.info("Fetching all library games.");
        List<Game> games = gameRepository.findAll();
        return games.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public GameDTO getLibraryGameById(String libraryGameId)
            throws ExecutionException, InterruptedException {
        logger.info("Fetching library game by ID: {}", libraryGameId);
        Game game = gameRepository.findById(libraryGameId)
                .orElseThrow(() -> {
                    logger.warn("Library game not found with ID: {}", libraryGameId);
                    return new ResourceNotFoundException("Game", "Library ID", libraryGameId);
                });
        return convertToDTO(game);
    }

    private GameDTO convertToDTO(Game game) {
        if (game == null) {
            return null;
        }
        GameDTO dto = new GameDTO();
        dto.setLibraryGameId(game.getLibraryGameId());
        dto.setTitle(game.getTitle());
        dto.setDescription(game.getDescription());
        dto.setGradeLevel(game.getGradeLevel());
        dto.setDifficulty(game.getDifficulty());
        dto.setGameUrlOrIdentifier(game.getGameUrlOrIdentifier());
        dto.setMaxXpAwarded(game.getMaxXpAwarded());
        dto.setTotalPointsPossible(game.getTotalPointsPossible());
        dto.setCreatedAt(game.getCreatedAt());
        dto.setUpdatedAt(game.getUpdatedAt());
        return dto;
    }

    // If, in the future, admins or a separate process needs to add games to the library via API:
    /*
    public GameDTO addGameToLibrary(CreateGameRequestDTO createRequest)
            throws ExecutionException, InterruptedException {
        // This would require CreateGameRequestDTO and potentially Question/Option models/DTOs
        // if game structure was managed internally.
        // For now, games are pre-made.
        Game game = new Game();
        game.setTitle(createRequest.getTitle());
        game.setDescription(createRequest.getDescription());
        game.setGradeLevel(createRequest.getGradeLevel());
        game.setDifficulty(createRequest.getDifficulty());
        // game.setGameUrlOrIdentifier(...);
        // game.setMaxXpAwarded(...);
        // game.setTotalPointsPossible(...);
        // game.setQuestions(...); // If questions were part of the model

        Game savedGame = gameRepository.save(game); // Assuming gameRepository has a save method
        logger.info("New game '{}' added to library with ID: {}", savedGame.getTitle(), savedGame.getLibraryGameId());
        return convertToDTO(savedGame);
    }
    */
}