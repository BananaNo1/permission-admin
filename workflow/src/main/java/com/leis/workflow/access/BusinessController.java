package com.leis.workflow.access;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/workflow/businesses")
public class BusinessController {
    private final BusinessAccess access;

    public BusinessController(BusinessAccess access) {
        this.access = access;
    }

    @GetMapping
    public List<BusinessAccess.Business> list() {
        return access.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@Valid @RequestBody BusinessAccess.Create request) {
        access.create(request);
    }

    @GetMapping("/{code}/grants")
    public List<BusinessAccess.Grant> grants(@PathVariable String code) {
        return access.grants(code);
    }

    @PutMapping("/{code}/grants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable String code, @PathVariable long userId, @RequestBody BusinessAccess.Rights request) {
        access.grant(code,userId,request);
    }
}
