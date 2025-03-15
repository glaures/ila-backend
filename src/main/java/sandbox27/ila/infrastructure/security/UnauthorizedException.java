package sandbox27.ila.infrastructure.security;

public class UnauthorizedException extends Exception {


    public UnauthorizedException(Exception e) {
        super(e);
    }
}
