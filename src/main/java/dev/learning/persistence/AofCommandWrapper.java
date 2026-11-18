package dev.learning.persistence;

import dev.learning.command.CommandDispatcher;
import dev.learning.protocol.RespValue;

public class AofCommandWrapper {
    private final CommandDispatcher dispatcher;
    private final AofService aofService;

    public AofCommandWrapper(CommandDispatcher dispatcher, AofService aofService) {
        this.dispatcher = dispatcher;
        this.aofService = aofService;
    }

    public RespValue dispatch(RespValue request) {
        return dispatcher.dispatch(request);
    }
}
