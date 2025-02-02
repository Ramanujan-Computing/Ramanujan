package in.ramanujan.devices.common.Credentials;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Credentials {
    private String publicKey;
    private String login;
}
