package com.gzplanet.xposed.xperiaphonevibrator;

/**
 * Defines call-state constants of the different states in which a call can exist. Although states
 * have the notion of normal transitions, due to the volatile nature of telephony systems, code
 * that uses these states should be resilient to unexpected state changes outside of what is
 * considered traditional.
 *
 * For Lollipop and later
 */

public final class CallState {

    private CallState() {}

    /**
     * Indicates that a call is new and not connected. This is used as the default state internally
     * within Telecom and should not be used between Telecom and call services. Call services are
     * not expected to ever interact with NEW calls, but {@link InCallService}s will see calls in
     * this state.
     */
    public static final int NEW = 0;

    /**
     * The initial state of an outgoing {@code Call}.
     * Common transitions are to {@link #DIALING} state for a successful call or
     * {@link #DISCONNECTED} if it failed.
     */
    public static final int CONNECTING = 1;

    /**
     * Indicates that the call is about to go into the outgoing and dialing state but is waiting for
     * user input before it proceeds. For example, where no default {@link PhoneAccount} is set,
     * this is the state where the InCallUI is waiting for the user to select a
     * {@link PhoneAccount} to call from.
     */
    public static final int PRE_DIAL_WAIT = 2;

    /**
     * Indicates that a call is outgoing and in the dialing state. A call transitions to this state
     * once an outgoing call has begun (e.g., user presses the dial button in Dialer). Calls in this
     * state usually transition to {@link #ACTIVE} if the call was answered or {@link #DISCONNECTED}
     * if the call was disconnected somehow (e.g., failure or cancellation of the call by the user).
     */
    public static final int DIALING = 3;

    /**
     * Indicates that a call is incoming and the user still has the option of answering, rejecting,
     * or doing nothing with the call. This state is usually associated with some type of audible
     * ringtone. Normal transitions are to {@link #ACTIVE} if answered or {@link #DISCONNECTED}
     * otherwise.
     */
    public static final int RINGING = 4;

    /**
     * Indicates that a call is currently connected to another party and a communication channel is
     * open between them. The normal transition to this state is by the user answering a
     * {@link #DIALING} call or a {@link #RINGING} call being answered by the other party.
     */
    public static final int ACTIVE = 5;

    /**
     * Indicates that the call is currently on hold. In this state, the call is not terminated
     * but no communication is allowed until the call is no longer on hold. The typical transition
     * to this state is by the user putting an {@link #ACTIVE} call on hold by explicitly performing
     * an action, such as clicking the hold button.
     */
    public static final int ON_HOLD = 6;

    /**
     * Indicates that a call is currently disconnected. All states can transition to this state
     * by the call service giving notice that the connection has been severed. When the user
     * explicitly ends a call, it will not transition to this state until the call service confirms
     * the disconnection or communication was lost to the call service currently responsible for
     * this call (e.g., call service crashes).
     */
    public static final int DISCONNECTED = 7;

    /**
     * Indicates that the call was attempted (mostly in the context of outgoing, at least at the
     * time of writing) but cancelled before it was successfully connected.
     */
    public static final int ABORTED = 8;

    /**
     * Indicates that the call is in the process of being disconnected and will transition next
     * to a {@link #DISCONNECTED} state.
     * <p>
     * This state is not expected to be communicated from the Telephony layer, but will be reported
     * to the InCall UI for calls where disconnection has been initiated by the user but the
     * ConnectionService has confirmed the call as disconnected.
     */
    public static final int DISCONNECTING = 9;

    public static String toString(int callState) {
        switch (callState) {
            case NEW:
                return "NEW";
            case CONNECTING:
                return "CONNECTING";
            case PRE_DIAL_WAIT:
                return "PRE_DIAL_WAIT";
            case DIALING:
                return "DIALING";
            case RINGING:
                return "RINGING";
            case ACTIVE:
                return "ACTIVE";
            case ON_HOLD:
                return "ON_HOLD";
            case DISCONNECTED:
                return "DISCONNECTED";
            case ABORTED:
                return "ABORTED";
            case DISCONNECTING:
                return "DISCONNECTING";
            default:
                return "UNKNOWN";
        }
    }
}
