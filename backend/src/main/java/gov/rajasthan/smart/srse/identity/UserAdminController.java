package gov.rajasthan.smart.srse.identity;

import gov.rajasthan.smart.srse.identity.admin.UserAdminDtos;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final AuthenticatedUserService authenticatedUserService;

    public UserAdminController(
            UserAdminService userAdminService, AuthenticatedUserService authenticatedUserService) {
        this.userAdminService = userAdminService;
        this.authenticatedUserService = authenticatedUserService;
    }

    @GetMapping
    public List<UserAdminDtos.UserSummary> listUsers() {
        return userAdminService.listUsers(authenticatedUserService.requireCurrentUser());
    }

    @GetMapping("/{id}")
    public UserAdminDtos.UserSummary getUser(@PathVariable long id) {
        return userAdminService.getUser(authenticatedUserService.requireCurrentUser(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminDtos.UserSummary createUser(@RequestBody UserAdminDtos.CreateUserRequest req) {
        return userAdminService.createUser(authenticatedUserService.requireCurrentUser(), req);
    }

    @PutMapping("/{id}")
    public UserAdminDtos.UserSummary updateUser(
            @PathVariable long id, @RequestBody UserAdminDtos.UpdateUserRequest req) {
        return userAdminService.updateUser(authenticatedUserService.requireCurrentUser(), id, req);
    }

    @PutMapping("/{id}/scopes")
    public UserAdminDtos.UserSummary replaceScopes(
            @PathVariable long id, @RequestBody UserAdminDtos.ReplaceScopesRequest req) {
        return userAdminService.replaceScopes(authenticatedUserService.requireCurrentUser(), id, req);
    }

    @PostMapping("/{id}/deactivate")
    public UserAdminDtos.UserSummary deactivate(@PathVariable long id) {
        return userAdminService.deactivate(authenticatedUserService.requireCurrentUser(), id);
    }

    @PostMapping("/{id}/reactivate")
    public UserAdminDtos.UserSummary reactivate(@PathVariable long id) {
        return userAdminService.reactivate(authenticatedUserService.requireCurrentUser(), id);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable long id, @RequestBody UserAdminDtos.ResetPasswordRequest req) {
        userAdminService.resetPassword(authenticatedUserService.requireCurrentUser(), id, req);
    }
}
