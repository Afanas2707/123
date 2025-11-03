package org.nobilis.nobichat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_chat_sessions")
public class UserChatSession extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title")
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_ui_message_id", referencedColumnName = "id")
    @ToString.Exclude
    private ChatMessage currentUiMessage;

    @Column(name = "active_scenario_id")
    private UUID activeScenarioId;

    @Column(name = "current_step_name")
    private String currentStepName;

    @Column(name = "scenario_context_json", columnDefinition = "text")
    private String scenarioContextJson;

    @Column(name = "is_in_constructor_mode")
    private Boolean isInConstructorMode;

    @Column(name = "is_in_preview_mode")
    private Boolean isInPreviewMode;

    @Column(name = "active_draft_id")
    private UUID activeDraftId;

    @Column(name = "current_preview_step_name")
    private String currentPreviewStepName;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ChatMessage> messages = new ArrayList<>();
}