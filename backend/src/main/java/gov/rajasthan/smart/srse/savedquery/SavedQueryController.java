package gov.rajasthan.smart.srse.savedquery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static gov.rajasthan.smart.srse.savedquery.SavedQueryDtos.CreateSavedQueryRequest;
import static gov.rajasthan.smart.srse.savedquery.SavedQueryDtos.SavedQueryDetail;
import static gov.rajasthan.smart.srse.savedquery.SavedQueryDtos.SavedQuerySummary;
import static gov.rajasthan.smart.srse.savedquery.SavedQueryDtos.ShareSavedQueryRequest;
import static gov.rajasthan.smart.srse.savedquery.SavedQueryDtos.UpdateSavedQueryRequest;

@RestController
@RequestMapping("/api/saved-queries")
public class SavedQueryController {

    private final SavedQueryService savedQueryService;

    public SavedQueryController(SavedQueryService savedQueryService) {
        this.savedQueryService = savedQueryService;
    }

    @GetMapping
    public List<SavedQuerySummary> list() {
        return savedQueryService.listForCurrentUser();
    }

    @GetMapping("/{id}")
    public SavedQueryDetail get(@PathVariable long id) {
        return savedQueryService.getForCurrentUser(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedQueryDetail create(@RequestBody CreateSavedQueryRequest body) {
        return savedQueryService.create(body);
    }

    @PutMapping("/{id}")
    public SavedQueryDetail update(@PathVariable long id, @RequestBody UpdateSavedQueryRequest body) {
        return savedQueryService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        savedQueryService.delete(id);
    }

    @PostMapping("/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void share(@PathVariable long id, @RequestBody ShareSavedQueryRequest body) {
        savedQueryService.share(id, body);
    }
}
