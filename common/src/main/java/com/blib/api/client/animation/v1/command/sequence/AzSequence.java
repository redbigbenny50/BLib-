package com.blib.api.client.animation.v1.command.sequence;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.AzTarget;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehavior;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.internal.client.animation.dispatch.command.stage.AzAnimationStage;
import com.blib.internal.client.animation.property.AzAnimationStageProperties;

/**
 * A reusable chain of animations with optional timed events - ported from BLib 3.1.13 and adapted to BLib's tracks.
 * <p>
 * Stages are added with {@link #play}, {@link #loop}, {@link #hold} or {@link #then(String, AzPlayBehavior)}, chain
 * style ({@code AzSequence.create().play("a").loop("b")}) or builder style ({@code AzSequence.builder()...build()}).
 * Adding a stage after a looping, holding or freezing stage throws, because that stage could never play. Events
 * ({@link #event}) are counted in ticks from the start of the sequence and delivered by {@link AzSequencePlayer}.
 * <p>
 * Instances are immutable: every method returns a new sequence.
 * <p>
 * Note (as in BLib): event ticks are not scaled by animation speed, and the track's transition length is added before
 * each stage on the client; allow for both when lining events up with keyframes.
 */
public final class AzSequence {

    private static final AzSequence EMPTY = new AzSequence(List.of(), List.of());

    private static final Set<AzPlayBehavior> NON_FINISHING_BEHAVIORS = Set.of(
        AzPlayBehaviors.LOOP,
        AzPlayBehaviors.PING_PONG,
        AzPlayBehaviors.HOLD_ON_LAST_FRAME,
        AzPlayBehaviors.FREEZE_ON_FRAME
    );

    private final List<AzAnimationStage> stages;

    private final List<AzSequenceEvent> events;

    private final AzAnimationSequence animationSequence;

    private AzSequence(List<AzAnimationStage> stages, List<AzSequenceEvent> events) {
        this.stages = List.copyOf(stages);

        var sortedEvents = new ArrayList<>(events);
        // Stable sort: events on the same tick fire in declaration order.
        sortedEvents.sort(Comparator.comparingInt(AzSequenceEvent::tick));
        this.events = List.copyOf(sortedEvents);

        this.animationSequence = new AzAnimationSequence(this.stages);
    }

    /** @return the empty sequence, to chain stages onto */
    public static AzSequence create() {
        return EMPTY;
    }

    /** @return a new builder */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param animationName the animation to add
     * @param playBehavior  how it plays
     * @return a new sequence with the stage added
     */
    public AzSequence then(String animationName, AzPlayBehavior playBehavior) {
        return toBuilder().then(animationName, playBehavior).build();
    }

    /**
     * @param animationName the animation to add
     * @param playBehavior  how it plays
     * @param customizer    adjusts the stage's properties (speed, easing, transition...)
     * @return a new sequence with the stage added
     */
    public AzSequence then(
        String animationName,
        AzPlayBehavior playBehavior,
        UnaryOperator<AzAnimationStageProperties> customizer
    ) {
        return toBuilder().then(animationName, playBehavior, customizer).build();
    }

    /**
     * @param other       a sequence to append
     * @param offsetTicks added to each of its events' ticks
     * @return a new sequence with the other's stages and events appended
     */
    public AzSequence then(AzSequence other, int offsetTicks) {
        return toBuilder().append(other, offsetTicks).build();
    }

    /**
     * @param animationName an animation to play once
     * @return a new sequence with the stage added
     */
    public AzSequence play(String animationName) {
        return toBuilder().play(animationName).build();
    }

    /**
     * @param animationName an animation to loop (must be the last stage)
     * @return a new sequence with the stage added
     */
    public AzSequence loop(String animationName) {
        return toBuilder().loop(animationName).build();
    }

    /**
     * @param animationName an animation to hold on its last frame (must be the last stage)
     * @return a new sequence with the stage added
     */
    public AzSequence hold(String animationName) {
        return toBuilder().hold(animationName).build();
    }

    /**
     * @param name the event's name
     * @param tick ticks from the start of the sequence
     * @return a new sequence with the event added
     */
    /**
     * Appends a stage that plays the animation the way its file says to (the {@code loop}, {@code repeat_times} and
     * {@code freeze_at} set in Blockbench). Only add further stages after it if the file's behavior finishes.
     */
    public AzSequence authored(String animationName) {
        return toBuilder().authored(animationName).build();
    }

    /**
     * Appends a stage that plays the animation forward and back indefinitely; see {@link AzPlayBehaviors#PING_PONG}.
     */
    public AzSequence pingPong(String animationName) {
        return toBuilder().pingPong(animationName).build();
    }

    /** Appends a stage that plays the animation once, backward. */
    public AzSequence playReversed(String animationName) {
        return toBuilder().playReversed(animationName).build();
    }

    public AzSequence event(String name, int tick) {
        return toBuilder().event(name, tick).build();
    }

    /** @return a builder holding this sequence's stages and events */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.stages.addAll(stages);
        builder.events.addAll(events);
        return builder;
    }

    /** @return the stages as BLib's plain animation sequence, as the track plays it */
    public AzAnimationSequence toAnimationSequence() {
        return animationSequence;
    }

    /**
     * @param trackName the track to play it on
     * @param <T>       the animatable type
     * @return a command that plays this sequence on that track
     */
    public <T> AzCommand<T> toCommand(String trackName) {
        return AzCommand.<T>builder().playSequence(AzTarget.track(trackName), this).build();
    }

    /** @return the stages */
    public List<AzAnimationStage> stages() {
        return stages;
    }

    /** @return the events, in tick order */
    public List<AzSequenceEvent> events() {
        return events;
    }

    /** @return whether there are no stages */
    public boolean isEmpty() {
        return stages.isEmpty();
    }

    /** @return whether there are events */
    public boolean hasEvents() {
        return !events.isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof AzSequence that)) {
            return false;
        }

        return stages.equals(that.stages) && events.equals(that.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stages, events);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder("AzSequence[");

        for (var i = 0; i < stages.size(); i++) {
            var stage = stages.get(i);

            if (i > 0) {
                builder.append(" -> ");
            }

            builder.append(stage.name()).append('(').append(stage.properties().playBehavior().name()).append(')');
        }

        if (!events.isEmpty()) {
            builder.append(", events=").append(events);
        }

        return builder.append(']').toString();
    }

    /** Builds an {@link AzSequence} step by step. */
    public static final class Builder {

        private final List<AzAnimationStage> stages = new ArrayList<>();

        private final List<AzSequenceEvent> events = new ArrayList<>();

        private Builder() {}

        /**
         * @param animationName the animation to add
         * @param playBehavior  how it plays
         * @return this builder
         */
        public Builder then(String animationName, AzPlayBehavior playBehavior) {
            return then(animationName, playBehavior, UnaryOperator.identity());
        }

        /**
         * @param animationName the animation to add
         * @param playBehavior  how it plays
         * @param customizer    adjusts the stage's properties
         * @return this builder
         */
        public Builder then(
            String animationName,
            @NotNull AzPlayBehavior playBehavior,
            @NotNull UnaryOperator<AzAnimationStageProperties> customizer
        ) {
            requireName(animationName, "Animation name");
            Objects.requireNonNull(playBehavior, "playBehavior");
            Objects.requireNonNull(customizer, "customizer");
            ensureNotAfterFinalStage(animationName);

            var properties = customizer.apply(AzAnimationStageProperties.EMPTY.withPlayBehavior(playBehavior));

            if (properties == null) {
                throw new IllegalArgumentException("Stage customizer for '" + animationName + "' returned null");
            }

            stages.add(new AzAnimationStage(animationName, properties));
            return this;
        }

        /**
         * @param other       a sequence to append
         * @param offsetTicks added to each of its events' ticks
         * @return this builder
         */
        public Builder append(AzSequence other, int offsetTicks) {
            Objects.requireNonNull(other, "other");

            if (offsetTicks < 0) {
                throw new IllegalArgumentException("offsetTicks must be >= 0, was " + offsetTicks);
            }

            if (!other.stages.isEmpty()) {
                ensureNotAfterFinalStage(other.stages.getFirst().name());
            }

            // AzureLib 3.2.2 (ported Oct 31) - anchored events move with their stage; tick events by the offset.
            var stageOffset = stages.size();
            stages.addAll(other.stages);
            other.events.forEach(event -> events.add(event.shifted(stageOffset, offsetTicks)));
            return this;
        }

        /**
         * @param animationName an animation to play once
         * @return this builder
         */
        public Builder play(String animationName) {
            return then(animationName, AzPlayBehaviors.PLAY_ONCE);
        }

        /**
         * @param animationName an animation to loop (must be the last stage)
         * @return this builder
         */
        public Builder loop(String animationName) {
            return then(animationName, AzPlayBehaviors.LOOP);
        }

        /**
         * @param animationName an animation to hold on its last frame (must be the last stage)
         * @return this builder
         */
        public Builder hold(String animationName) {
            return then(animationName, AzPlayBehaviors.HOLD_ON_LAST_FRAME);
        }

        public Builder authored(String animationName) {
            return then(animationName, AzPlayBehaviors.AS_AUTHORED);
        }

        public Builder pingPong(String animationName) {
            return then(animationName, AzPlayBehaviors.PING_PONG);
        }

        /**
         * Plays the animation once, backward. For other behaviors, use
         * {@code then(name, behavior, p -> p.withShouldReverse(true))}.
         */
        public Builder playReversed(String animationName) {
            return then(animationName, AzPlayBehaviors.PLAY_ONCE, properties -> properties.withShouldReverse(true));
        }

        public Builder event(String name, int tick) {
            requireName(name, "Event name");

            if (tick < 0) {
                throw new IllegalArgumentException("Event '" + name + "' has a negative tick: " + tick);
            }

            events.add(new AzSequenceEvent(name, tick));
            return this;
        }

        /**
         * AzureLib 3.2.2 (ported Oct 31) - a stage that plays until {@code freezeTick} (animation ticks) and holds that
         * pose. Like {@code then(name, FREEZE_ON_FRAME, p -> p.withFreezeTickOffset(freezeTick))}.
         *
         * @param animationName the animation to play
         * @param freezeTick    the tick on the animation's own timeline to freeze on; must be above 0
         * @return this builder
         */
        public Builder freeze(String animationName, double freezeTick) {
            if (!(freezeTick > 0)) {
                throw new IllegalArgumentException("Freeze tick for '" + animationName + "' must be above 0, was " + freezeTick);
            }

            return then(animationName, AzPlayBehaviors.FREEZE_ON_FRAME, properties -> properties.withFreezeTickOffset(freezeTick));
        }

        /**
         * AzureLib 3.2.2 (ported Oct 31) - an event when the LAST stage added so far reaches {@code keyframeTick} on
         * its own animation timeline (the tick Blockbench shows). Unlike {@link #event}, it follows playback speed,
         * start offsets, transitions, repeats and earlier stages' lengths. Needs the animation lengths: on the client
         * the player reads them from the animation file; on the server pass them in with {@link AzSequenceTiming}.
         *
         * @param name         the event name
         * @param keyframeTick the tick on the stage animation's timeline
         * @return this builder
         */
        public Builder eventAt(String name, double keyframeTick) {
            requireName(name, "Event name");

            if (keyframeTick < 0) {
                throw new IllegalArgumentException("Event '" + name + "' has a negative keyframe tick: " + keyframeTick);
            }

            events.add(new AzSequenceEvent(name, 0, lastStageIndex(name), keyframeTick, AzSequenceEvent.Anchor.KEYFRAME));
            return this;
        }

        /**
         * AzureLib 3.2.2 (ported Oct 31) - an event when the last stage added so far starts playing (after the
         * transition into it).
         *
         * @param name the event name
         * @return this builder
         */
        public Builder eventOnStart(String name) {
            requireName(name, "Event name");
            events.add(new AzSequenceEvent(name, 0, lastStageIndex(name), 0, AzSequenceEvent.Anchor.STAGE_START));
            return this;
        }

        /**
         * AzureLib 3.2.2 (ported Oct 31) - an event when the last stage added so far finishes: after its last repeat,
         * at its freeze point, or at the end of the first pass for looping, ping-pong and holding stages.
         *
         * @param name the event name
         * @return this builder
         */
        public Builder eventOnEnd(String name) {
            requireName(name, "Event name");
            events.add(new AzSequenceEvent(name, 0, lastStageIndex(name), 0, AzSequenceEvent.Anchor.STAGE_END));
            return this;
        }

        private int lastStageIndex(String eventName) {
            if (stages.isEmpty()) {
                throw new IllegalStateException("Event '" + eventName + "' is anchored to a stage, but no stage was added yet");
            }

            return stages.size() - 1;
        }

        /** @return the sequence */
        public AzSequence build() {
            if (stages.isEmpty() && events.isEmpty()) {
                return EMPTY;
            }

            return new AzSequence(stages, events);
        }

        private void ensureNotAfterFinalStage(String nextAnimationName) {
            if (stages.isEmpty()) {
                return;
            }

            var last = stages.getLast();
            var lastBehavior = last.properties().playBehavior();

            if (NON_FINISHING_BEHAVIORS.contains(lastBehavior)) {
                throw new IllegalStateException(
                    "Cannot add '" + nextAnimationName + "' after '" + last.name() + "': its play behavior '"
                        + lastBehavior.name() + "' never finishes, so the stage would never play."
                );
            }
        }

        private static void requireName(String value, String what) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(what + " must not be null or blank");
            }
        }
    }
}
