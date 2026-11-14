package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.Guard;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuardControllerTest {

    @Test
    void createIgnoresClientId() throws Exception {
        GuardRepository repository = mock(GuardRepository.class);
        when(repository.save(any(Guard.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new GuardController(repository))
                .build();

        mockMvc.perform(post("/api/guards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"existente\",\"name\":\"Turno noche\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Guard> saved = ArgumentCaptor.forClass(Guard.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
    }
}
