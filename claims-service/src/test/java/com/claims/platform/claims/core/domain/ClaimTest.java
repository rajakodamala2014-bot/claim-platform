package com.claims.platform.claims.core.domain;

import com.claims.platform.common.event.EventTypes;
import com.claims.platform.common.money.Money;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.claims.platform.claims.core.domain.ClaimFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {

    @Nested
    class Intake {
        @Test
        void customerSubmissionStartsSubmittedWithCoverPendingAndSlaClock() {
            Claim claim = submitted();

            assertThat(claim.status()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(claim.coverStatus()).isEqualTo(CoverStatus.PENDING);
            assertThat(claim.slaDueAt()).isEqualTo(T0.plus(SLA));
            assertThat(claim.outstandingReserve().isZero()).isTrue();
            ClaimEvent submittedEvent = only(claim, EventTypes.CLAIM_SUBMITTED);
            assertThat(submittedEvent.visibility()).isEqualTo(ClaimEvent.Visibility.CLAIMANT);
            assertThat(submittedEvent.data()).containsEntry("claimNumber", "SG-MTR-2026-000123")
                    .containsEntry("market", "SG").containsEntry("lineOfBusiness", "MOTOR")
                    .containsEntry("policyNumber", "SG-MTR-POL-1001").containsEntry("channel", "APP")
                    .containsEntry("claimedAmountMinor", 600_000L);
        }

        @Test
        void customerCanOnlySubmitForThemselves() {
            expectCode(() -> Claim.submit(newClaim(Channel.APP), OTHER_CUSTOMER, T0), ErrorCode.NOT_CLAIM_OWNER);
            expectCode(() -> Claim.submit(newClaim(Channel.APP), ARUN, T0), ErrorCode.NOT_CLAIM_OWNER);
        }

        @Test
        void onlyStaffRegisterOnTheCustomersBehalf() {
            Claim claim = Claim.submit(newClaim(Channel.STAFF), ARUN, T0);

            assertThat(only(claim, EventTypes.CLAIM_SUBMITTED).actorId()).isEqualTo(ARUN.id());
            expectCode(() -> Claim.submit(newClaim(Channel.STAFF), PRIYA, T0), ErrorCode.FORBIDDEN_ROLE);
        }

        @Test
        void confirmedCoverTriagesTheClaim() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SUBMITTED);

            claim.confirmCover(Actor.SYSTEM, at(1));

            assertThat(claim.status()).isEqualTo(ClaimStatus.TRIAGED);
            assertThat(claim.coverStatus()).isEqualTo(CoverStatus.CONFIRMED);
            assertThat(types(claim)).containsExactly(EventTypes.CLAIM_TRIAGED);
        }

        @Test
        void declinedCoverIsTriagedAndFlaggedForAHandler() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SUBMITTED);

            claim.declineCover("POLICY_NOT_IN_FORCE", Actor.SYSTEM, at(1));

            assertThat(claim.status()).isEqualTo(ClaimStatus.TRIAGED);
            assertThat(claim.coverStatus()).isEqualTo(CoverStatus.DECLINED);
            assertThat(claim.coverDeclineReason()).contains("POLICY_NOT_IN_FORCE");
            assertThat(types(claim)).containsExactly(EventTypes.COVER_DECLINED, EventTypes.CLAIM_TRIAGED);
            assertThat(claim.pendingEvents().getFirst().visibility()).isEqualTo(ClaimEvent.Visibility.INTERNAL);
        }

        @Test
        void claimNumberMustMatchMarketAndLineOfBusiness() {
            assertThatThrownBy(() -> new NewClaim(ClaimId.newId(),
                    ClaimNumber.of(MarketCode.of("HK"), LineOfBusiness.MOTOR, 2026, 1), MarketCode.of("SG"),
                    LineOfBusiness.MOTOR, "P", "c", Channel.APP, "SGD", T0, null, SLA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Assignment {
        @Test
        void handlerPicksUpATriagedClaim() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.TRIAGED);

            claim.assign(ARUN.id(), "team-sg-motor", ARUN, at(10));

            assertThat(claim.status()).isEqualTo(ClaimStatus.ASSIGNED);
            assertThat(claim.handlerId()).contains(ARUN.id());
            assertThat(claim.teamId()).contains("team-sg-motor");
            assertThat(only(claim, EventTypes.CLAIM_ASSIGNED).data()).containsEntry("handlerId", ARUN.id());
        }

        @Test
        void secondHandlerGetsAlreadyAssigned() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.ASSIGNED);

            expectCode(() -> claim.assign(KUMAR.id(), "team-sg-motor", KUMAR, at(11)), ErrorCode.CLAIM_ALREADY_ASSIGNED);
            assertThat(claim.handlerId()).contains(ARUN.id());
        }

        @Test
        void handlerCannotAssignSomeoneElse() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.TRIAGED);

            expectCode(() -> claim.assign(KUMAR.id(), "t", ARUN, at(10)), ErrorCode.FORBIDDEN_ROLE);
        }

        @Test
        void customerCannotAssign() {
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.TRIAGED).assign(PRIYA.id(), "t", PRIYA, at(10)),
                    ErrorCode.FORBIDDEN_ROLE);
        }

        @Test
        void supervisorReassignsWithoutChangingStatus() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            claim.assign(KUMAR.id(), "team-sg-motor", MEERA, at(30));

            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.handlerId()).contains(KUMAR.id());
            assertThat(only(claim, EventTypes.CLAIM_ASSIGNED).data())
                    .containsEntry("previousHandlerId", ARUN.id()).containsEntry("handlerId", KUMAR.id());
        }

        @Test
        void reassigningToTheSameHandlerIsANoOp() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.ASSIGNED);

            claim.assign(ARUN.id(), "team-sg-motor", MEERA, at(30));

            assertThat(claim.pendingEvents()).isEmpty();
        }

        @Test
        void onlyTheAssignedHandlerOrASupervisorWorksTheClaim() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.ASSIGNED);

            expectCode(() -> claim.startAssessment(KUMAR, at(20)), ErrorCode.NOT_ASSIGNED_HANDLER);
            expectCode(() -> claim.startAssessment(PRIYA, at(20)), ErrorCode.FORBIDDEN_ROLE);
            claim.startAssessment(ARUN, at(20));
            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(only(claim, EventTypes.ASSESSMENT_STARTED).visibility()).isEqualTo(ClaimEvent.Visibility.CLAIMANT);
        }
    }

    @Nested
    class Reserves {
        @Test
        void reserveChangesAreLedgerMovementsWithEvents() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT); // 5000 initial

            claim.changeReserve(sgd("7500"), "Repair quote received", ARUN, at(31));

            assertThat(claim.outstandingReserve()).isEqualTo(sgd("7500"));
            assertThat(claim.reserveMovements()).extracting(ReserveMovement::amount).containsExactly(sgd("5000"), sgd("2500"));
            ClaimEvent event = only(claim, EventTypes.RESERVE_CHANGED);
            assertThat(event.visibility()).isEqualTo(ClaimEvent.Visibility.INTERNAL);
            assertThat(event.data()).containsEntry("movementMinor", 250_000L)
                    .containsEntry("newOutstandingMinor", 750_000L).containsEntry("currency", "SGD");
        }

        @Test
        void sameTotalRecordsNothing() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            claim.changeReserve(sgd("5000"), "No change", ARUN, at(31));

            assertThat(claim.pendingEvents()).isEmpty();
        }

        @Test
        void reserveCannotBeSetBeforeAssignmentOrAfterTheOutcome() {
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.TRIAGED).changeReserve(sgd("1"), "x", MEERA, at(1)),
                    ErrorCode.CLAIM_INVALID_TRANSITION);
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.SETTLED).changeReserve(sgd("1"), "x", MEERA, at(1)),
                    ErrorCode.CLAIM_INVALID_TRANSITION);
        }

        @Test
        void reserveMustBeInClaimCurrency() {
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT)
                    .changeReserve(Money.of("1", "HKD"), "x", ARUN, at(1)), ErrorCode.CURRENCY_MISMATCH);
        }
    }

    @Nested
    class InformationRequests {
        @Test
        void requestPausesSlaAndAnswerResumesItExtendingTheDeadline() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);
            var originalDue = claim.slaDueAt();

            claim.requestInformation(REQUEST_ID, List.of("Police report"), at(60 * 24 * 7), ARUN, at(30));
            assertThat(claim.status()).isEqualTo(ClaimStatus.AWAITING_INFO);
            assertThat(claim.isSlaPaused()).isTrue();
            assertThat(claim.openInformationRequest()).isPresent();

            UUID doc = UUID.randomUUID();
            claim.provideInformation(REQUEST_ID, "Report attached", List.of(doc), PRIYA, at(30 + 180));

            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.isSlaPaused()).isFalse();
            assertThat(claim.slaDueAt()).isEqualTo(originalDue.plus(Duration.ofMinutes(180)));
            assertThat(claim.openInformationRequest()).isEmpty();
            assertThat(claim.informationRequests().getFirst().status()).isEqualTo(InformationRequest.Status.ANSWERED);
            assertThat(types(claim)).containsExactly(EventTypes.INFORMATION_REQUESTED, EventTypes.INFORMATION_PROVIDED);
            assertThat(claim.pendingEvents().get(1).data()).containsEntry("documentIds", List.of(doc.toString()));
        }

        @Test
        void onlyTheOwnerCanAnswerAndOnlyAnOpenRequest() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.AWAITING_INFO);

            expectCode(() -> claim.provideInformation(REQUEST_ID, "x", List.of(), OTHER_CUSTOMER, at(40)), ErrorCode.NOT_CLAIM_OWNER);
            expectCode(() -> claim.provideInformation(REQUEST_ID, "x", List.of(), ARUN, at(40)), ErrorCode.NOT_CLAIM_OWNER);
            expectCode(() -> claim.provideInformation(UUID.randomUUID(), "x", List.of(), PRIYA, at(40)), ErrorCode.INFO_REQUEST_NOT_FOUND);
            assertThat(claim.status()).isEqualTo(ClaimStatus.AWAITING_INFO);
        }

        @Test
        void requestNeedsItemsAndAFutureDueDate() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            expectCode(() -> claim.requestInformation(REQUEST_ID, List.of(), at(9999), ARUN, at(30)), ErrorCode.VALIDATION_FAILED);
            expectCode(() -> claim.requestInformation(REQUEST_ID, List.of("x"), at(29), ARUN, at(30)), ErrorCode.VALIDATION_FAILED);
        }

        @Test
        void expiryReturnsTheClaimToTheHandlerOnlyOnceDue() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.AWAITING_INFO); // due at(7 days)

            expectCode(() -> claim.expireInformationRequest(REQUEST_ID, at(60)), ErrorCode.VALIDATION_FAILED);
            claim.expireInformationRequest(REQUEST_ID, at(60 * 24 * 7));

            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.isSlaPaused()).isFalse();
            assertThat(claim.informationRequests().getFirst().status()).isEqualTo(InformationRequest.Status.EXPIRED);
            assertThat(only(claim, EventTypes.INFORMATION_REQUEST_EXPIRED).actorRole()).isEqualTo(Role.SYSTEM);
        }
    }

    @Nested
    class Assessments {
        @Test
        void recordsAssessmentInternallyWithoutStatusChange() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            claim.recordAssessment(UUID.randomUUID(), Assessment.Liability.PARTIAL, sgd("4200"), "50/50 at junction", ARUN, at(35));

            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.assessment()).get().extracting(Assessment::liability).isEqualTo(Assessment.Liability.PARTIAL);
            ClaimEvent event = only(claim, EventTypes.ASSESSMENT_RECORDED);
            assertThat(event.visibility()).isEqualTo(ClaimEvent.Visibility.INTERNAL);
            assertThat(event.data()).doesNotContainKey("notes");
        }

        @Test
        void onlyWhileUnderAssessment() {
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.AWAITING_INFO).recordAssessment(UUID.randomUUID(),
                    Assessment.Liability.FULL, sgd("1"), "", MEERA, at(1)), ErrorCode.CLAIM_INVALID_TRANSITION);
        }
    }

    @Nested
    class Decisions {
        @Test
        void approvalWithinAuthorityIsFinalImmediately() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            Decision decision = claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, sgd("4200"), null,
                    ApprovalRequirement.NONE, ARUN, at(40));

            assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(claim.approvedAmount()).contains(sgd("4200"));
            assertThat(decision.status()).isEqualTo(Decision.Status.FINAL);
            assertThat(types(claim)).containsExactly(EventTypes.DECISION_PROPOSED, EventTypes.CLAIM_APPROVED);
            assertThat(claim.pendingEvents().getFirst().data()).containsEntry("approvalRequired", false);
            assertThat(claim.pendingEvents().get(1).data()).containsEntry("approvedAmountMinor", 420_000L);
        }

        @Test
        void rejectionWithinAuthorityReleasesTheReserve() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            claim.proposeDecision(DECISION_ID, Decision.Outcome.REJECT, sgd("5000"), "PERIL_NOT_COVERED",
                    ApprovalRequirement.NONE, ARUN, at(40));

            assertThat(claim.status()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(claim.outstandingReserve().isZero()).isTrue();
            assertThat(types(claim)).containsExactly(EventTypes.DECISION_PROPOSED, EventTypes.CLAIM_REJECTED,
                    EventTypes.RESERVE_CHANGED);
            assertThat(claim.pendingEvents().get(1).data()).containsEntry("reasonCode", "PERIL_NOT_COVERED");
        }

        @Test
        void aboveAuthorityWaitsForASupervisor() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            Decision decision = claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, sgd("12000"), null,
                    ApprovalRequirement.SUPERVISOR_APPROVAL, ARUN, at(40));

            assertThat(claim.status()).isEqualTo(ClaimStatus.DECISION_PENDING);
            assertThat(decision.status()).isEqualTo(Decision.Status.AWAITING_APPROVAL);
            assertThat(types(claim)).containsExactly(EventTypes.DECISION_PROPOSED);
            assertThat(claim.pendingEvents().getFirst().data()).containsEntry("approvalRequired", true);
        }

        @Test
        void supervisorApprovesAPendingDecision() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.DECISION_PENDING);

            claim.approveDecision(DECISION_ID, MEERA, at(45));

            assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(claim.decision()).get().satisfies(d -> {
                assertThat(d.status()).isEqualTo(Decision.Status.FINAL);
                assertThat(d.decidedBy()).isEqualTo(MEERA.id());
            });
            assertThat(types(claim)).containsExactly(EventTypes.DECISION_APPROVED, EventTypes.CLAIM_APPROVED);
        }

        @Test
        void supervisorReturnsAPendingDecision() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.DECISION_PENDING);

            claim.returnDecision(DECISION_ID, "Get the repair quote first", MEERA, at(45));

            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.decision()).get().extracting(Decision::status).isEqualTo(Decision.Status.RETURNED);
            assertThat(only(claim, EventTypes.DECISION_RETURNED).data()).containsEntry("comment", "Get the repair quote first");
        }

        @Test
        void proposerCannotApproveTheirOwnDecision() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);
            claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, sgd("80000"), null,
                    ApprovalRequirement.SUPERVISOR_APPROVAL, MEERA, at(40));
            claim.pullPendingEvents();

            expectCode(() -> claim.approveDecision(DECISION_ID, MEERA, at(41)), ErrorCode.SELF_APPROVAL_NOT_ALLOWED);
            claim.approveDecision(DECISION_ID, DAVID, at(42));
            assertThat(claim.status()).isEqualTo(ClaimStatus.APPROVED);
        }

        @Test
        void handlersCannotApproveAndUnknownDecisionsAreRejected() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.DECISION_PENDING);

            expectCode(() -> claim.approveDecision(DECISION_ID, KUMAR, at(41)), ErrorCode.FORBIDDEN_ROLE);
            expectCode(() -> claim.approveDecision(UUID.randomUUID(), MEERA, at(41)), ErrorCode.DECISION_NOT_FOUND);
            expectCode(() -> claim.returnDecision(DECISION_ID, " ", MEERA, at(41)), ErrorCode.VALIDATION_FAILED);
            assertThat(claim.status()).isEqualTo(ClaimStatus.DECISION_PENDING);
        }

        @Test
        void aFinalDecisionCannotBeApprovedAgain() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.APPROVED);

            expectCode(() -> claim.approveDecision(DECISION_ID, MEERA, at(41)), ErrorCode.DECISION_NOT_FOUND);
        }

        @Test
        void proposalValidation() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT);

            expectCode(() -> claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, sgd("0"), null,
                    ApprovalRequirement.NONE, ARUN, at(40)), ErrorCode.VALIDATION_FAILED);
            expectCode(() -> claim.proposeDecision(DECISION_ID, Decision.Outcome.REJECT, sgd("0"), " ",
                    ApprovalRequirement.NONE, ARUN, at(40)), ErrorCode.VALIDATION_FAILED);
            expectCode(() -> claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, Money.of("1", "MYR"), null,
                    ApprovalRequirement.NONE, ARUN, at(40)), ErrorCode.CURRENCY_MISMATCH);
            expectCode(() -> claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, sgd("1"), null,
                    ApprovalRequirement.NONE, KUMAR, at(40)), ErrorCode.NOT_ASSIGNED_HANDLER);
            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.pendingEvents()).isEmpty();
        }
    }

    @Nested
    class Settlements {
        @Test
        void instructingPaymentMovesToSettlementInProgress() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.APPROVED);

            Settlement settlement = claim.instructSettlement(SETTLEMENT_ID, INSTRUCTION_ID, PAYEE, sgd("4200"), ARUN, at(50));

            assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLEMENT_IN_PROGRESS);
            assertThat(settlement.status()).isEqualTo(Settlement.Status.INSTRUCTED);
            assertThat(settlement.attempts()).isEqualTo(1);
            assertThat(only(claim, EventTypes.SETTLEMENT_INSTRUCTED).data())
                    .containsEntry("amountMinor", 420_000L).containsEntry("attempt", 1)
                    .doesNotContainKeys("accountNumber", "bankCode");
        }

        @Test
        void settlementCannotExceedTheApprovedAmountButMayBeLess() {
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.APPROVED).instructSettlement(SETTLEMENT_ID, INSTRUCTION_ID,
                    PAYEE, sgd("4200.01"), ARUN, at(50)), ErrorCode.SETTLEMENT_EXCEEDS_APPROVED);

            Claim claim = ClaimFixture.inStatus(ClaimStatus.APPROVED);
            claim.instructSettlement(SETTLEMENT_ID, INSTRUCTION_ID, PAYEE, sgd("4000"), ARUN, at(50));
            assertThat(claim.settlement()).get().extracting(Settlement::amount).isEqualTo(sgd("4000"));
        }

        @Test
        void confirmedPaymentSettlesAndReleasesTheReserve() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS);

            boolean applied = claim.confirmPayment(SETTLEMENT_ID, INSTRUCTION_ID, "PAY-77", at(55), at(56));

            assertThat(applied).isTrue();
            assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLED);
            assertThat(claim.outstandingReserve().isZero()).isTrue();
            assertThat(claim.settlement()).get().extracting(Settlement::paymentRef).isEqualTo("PAY-77");
            assertThat(types(claim)).containsExactly(EventTypes.PAYMENT_CONFIRMED, EventTypes.CLAIM_SETTLED,
                    EventTypes.RESERVE_CHANGED);
            assertThat(claim.reserveMovements().getLast().type()).isEqualTo(ReserveMovement.Type.RELEASE);
        }

        @Test
        void duplicatePaymentResultIsIgnored() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SETTLED);

            assertThat(claim.confirmPayment(SETTLEMENT_ID, INSTRUCTION_ID, "PAY-1", at(57), at(57))).isFalse();
            assertThat(claim.failPayment(SETTLEMENT_ID, INSTRUCTION_ID, "late", at(57))).isFalse();
            assertThat(claim.pendingEvents()).isEmpty();
        }

        @Test
        void failedPaymentCanBeRetriedWithCorrectedDetailsAndStaleResultsAreIgnored() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS);

            assertThat(claim.failPayment(SETTLEMENT_ID, INSTRUCTION_ID, "ACCOUNT_CLOSED", at(55))).isTrue();
            assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLEMENT_IN_PROGRESS);
            assertThat(only(claim, EventTypes.PAYMENT_FAILED).data()).containsEntry("handlerId", ARUN.id());
            claim.pullPendingEvents();

            UUID retryInstruction = UUID.randomUUID();
            Payee corrected = new Payee("Priya Nair", "DBSSSGSG", "987654321");
            Settlement retried = claim.retrySettlement(SETTLEMENT_ID, retryInstruction, corrected, ARUN, at(60));
            assertThat(retried.attempts()).isEqualTo(2);
            assertThat(retried.payee()).isEqualTo(corrected);
            assertThat(only(claim, EventTypes.SETTLEMENT_INSTRUCTED).visibility()).isEqualTo(ClaimEvent.Visibility.INTERNAL);
            claim.pullPendingEvents();

            assertThat(claim.confirmPayment(SETTLEMENT_ID, INSTRUCTION_ID, "OLD", at(61), at(61))).isFalse();
            assertThat(claim.confirmPayment(SETTLEMENT_ID, retryInstruction, "PAY-2", at(62), at(62))).isTrue();
            assertThat(claim.status()).isEqualTo(ClaimStatus.SETTLED);
        }

        @Test
        void onlyAFailedPaymentIsRetryable() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SETTLEMENT_IN_PROGRESS);

            expectCode(() -> claim.retrySettlement(SETTLEMENT_ID, UUID.randomUUID(), null, ARUN, at(60)),
                    ErrorCode.SETTLEMENT_NOT_RETRYABLE);
            expectCode(() -> claim.retrySettlement(UUID.randomUUID(), UUID.randomUUID(), null, ARUN, at(60)),
                    ErrorCode.SETTLEMENT_NOT_FOUND);
        }

        @Test
        void payeeToStringMasksTheAccountNumber() {
            assertThat(PAYEE.toString()).doesNotContain("123456789").contains("****6789");
        }
    }

    @Nested
    class CustomerLifecycle {
        @Test
        void withdrawalBeforeDecisionReleasesReserveAndCancelsOpenRequest() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.AWAITING_INFO);

            claim.withdraw("Settled privately", PRIYA, at(100));

            assertThat(claim.status()).isEqualTo(ClaimStatus.WITHDRAWN);
            assertThat(claim.outstandingReserve().isZero()).isTrue();
            assertThat(claim.isSlaPaused()).isFalse();
            assertThat(claim.informationRequests().getFirst().status()).isEqualTo(InformationRequest.Status.CANCELLED);
            assertThat(types(claim)).containsExactly(EventTypes.CLAIM_WITHDRAWN, EventTypes.RESERVE_CHANGED);
        }

        @Test
        void onlyTheOwnerCanWithdrawOrAppeal() {
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.TRIAGED).withdraw("x", OTHER_CUSTOMER, at(1)), ErrorCode.NOT_CLAIM_OWNER);
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.TRIAGED).withdraw("x", ARUN, at(1)), ErrorCode.NOT_CLAIM_OWNER);
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.REJECTED).appeal("x", OTHER_CUSTOMER, at(1)), ErrorCode.NOT_CLAIM_OWNER);
        }

        @Test
        void appealReopensARejectedClaimForTheSameHandler() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.REJECTED);

            claim.appeal("I have a witness statement", PRIYA, at(200));

            assertThat(claim.status()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(claim.handlerId()).contains(ARUN.id());
            assertThat(claim.decision()).get().extracting(Decision::status).isEqualTo(Decision.Status.SUPERSEDED);
            assertThat(only(claim, EventTypes.CLAIM_REOPENED).data()).containsEntry("reason", "I have a witness statement");
            expectCode(() -> ClaimFixture.inStatus(ClaimStatus.REJECTED).appeal(" ", PRIYA, at(1)), ErrorCode.VALIDATION_FAILED);
        }

        @Test
        void autoCloseOnlyAfterCoolingOff() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.SETTLED); // settled at(55)
            Duration coolingOff = Duration.ofDays(14);

            assertThat(claim.isDueForClosure(at(55).plus(Duration.ofDays(13)), coolingOff)).isFalse();
            assertThat(claim.isDueForClosure(at(55).plus(coolingOff), coolingOff)).isTrue();
            assertThat(ClaimFixture.inStatus(ClaimStatus.UNDER_ASSESSMENT).isDueForClosure(at(99_999), coolingOff)).isFalse();

            claim.close(at(55).plus(coolingOff));
            assertThat(claim.status()).isEqualTo(ClaimStatus.CLOSED);
            assertThat(only(claim, EventTypes.CLAIM_CLOSED).data()).containsEntry("previousStatus", "SETTLED");
        }
    }

    @Nested
    class Events {
        @Test
        void fullHappyPathProducesTheSpecifiedEventSequence() {
            Claim claim = submitted();
            claim.confirmCover(Actor.SYSTEM, at(1));
            claim.assign(ARUN.id(), "team-sg-motor", ARUN, at(10));
            claim.startAssessment(ARUN, at(20));
            claim.changeReserve(sgd("5000"), "Initial", ARUN, at(21));
            claim.requestInformation(REQUEST_ID, List.of("Police report"), at(10_000), ARUN, at(22));
            claim.provideInformation(REQUEST_ID, "Attached", List.of(UUID.randomUUID()), PRIYA, at(23));
            claim.recordAssessment(UUID.randomUUID(), Assessment.Liability.FULL, sgd("4200"), "Rear-ended", ARUN, at(24));
            claim.proposeDecision(DECISION_ID, Decision.Outcome.APPROVE, sgd("4200"), null, ApprovalRequirement.NONE, ARUN, at(25));
            claim.instructSettlement(SETTLEMENT_ID, INSTRUCTION_ID, PAYEE, sgd("4200"), ARUN, at(26));
            claim.confirmPayment(SETTLEMENT_ID, INSTRUCTION_ID, "PAY-1", at(27), at(27));
            claim.close(at(27 + 60 * 24 * 14));

            assertThat(types(claim)).containsExactly(
                    EventTypes.CLAIM_SUBMITTED, EventTypes.CLAIM_TRIAGED, EventTypes.CLAIM_ASSIGNED,
                    EventTypes.ASSESSMENT_STARTED, EventTypes.RESERVE_CHANGED, EventTypes.INFORMATION_REQUESTED,
                    EventTypes.INFORMATION_PROVIDED, EventTypes.ASSESSMENT_RECORDED, EventTypes.DECISION_PROPOSED,
                    EventTypes.CLAIM_APPROVED, EventTypes.SETTLEMENT_INSTRUCTED, EventTypes.PAYMENT_CONFIRMED,
                    EventTypes.CLAIM_SETTLED, EventTypes.RESERVE_CHANGED, EventTypes.CLAIM_CLOSED);
            assertThat(claim.pendingEvents()).allSatisfy(e -> assertThat(EventTypes.CLAIM_EVENTS).contains(e.type()));
            assertThat(claim.pendingEvents()).extracting(ClaimEvent::eventId).doesNotHaveDuplicates();
        }

        @Test
        void internalDetailsAreNeverClaimantVisible() {
            Claim claim = ClaimFixture.inStatus(ClaimStatus.DECISION_PENDING);
            claim.approveDecision(DECISION_ID, MEERA, at(45));
            List<String> internalOnly = List.of(EventTypes.RESERVE_CHANGED, EventTypes.ASSESSMENT_RECORDED,
                    EventTypes.DECISION_PROPOSED, EventTypes.DECISION_APPROVED, EventTypes.DECISION_RETURNED,
                    EventTypes.COVER_DECLINED, EventTypes.PAYMENT_FAILED);

            assertThat(claim.pendingEvents()).filteredOn(e -> internalOnly.contains(e.type()))
                    .isNotEmpty()
                    .allSatisfy(e -> assertThat(e.visibility()).isEqualTo(ClaimEvent.Visibility.INTERNAL));
        }

        @Test
        void pullingEventsClearsThem() {
            Claim claim = submitted();

            assertThat(claim.pullPendingEvents()).hasSize(1);
            assertThat(claim.pendingEvents()).isEmpty();
        }
    }

    private static List<String> types(Claim claim) {
        return claim.pendingEvents().stream().map(ClaimEvent::type).toList();
    }

    private static ClaimEvent only(Claim claim, String type) {
        List<ClaimEvent> matching = claim.pendingEvents().stream().filter(e -> e.type().equals(type)).toList();
        assertThat(matching).as("events of type %s", type).hasSize(1);
        return matching.getFirst();
    }

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }
}
