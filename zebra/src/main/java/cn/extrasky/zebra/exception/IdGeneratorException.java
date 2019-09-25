package cn.extrasky.zebra.exception;

/**
 * @author YangGuodong
 * @date: 2019-08-21
 */
public class IdGeneratorException extends Exception {


    private String message;
    /**
     * Constructs a new <code>IdGeneratorException</code>
     */
    public IdGeneratorException() {
        super();
    }


    public IdGeneratorException(String message){
        super(message);
        this.message = message;
    }
    /**
     * Constructs a new <code>IdGeneratorException</code> with the specified
     * <code>Throwable</code> as the underlying reason.
     * @param cause the underlying cause of the exception.
     */
    public IdGeneratorException(Throwable cause) {
        super(cause);
    }
    /**
     * Constructs a new <code>IdGeneratorException</code> with the specified
     * <code>Throwable</code> as the underlying reason.
     * @param message the reason code for the exception.
     * @param cause the underlying cause of the exception.
     */
    public IdGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }


    @Override
    public String getMessage() {
        return message;
    }
}
