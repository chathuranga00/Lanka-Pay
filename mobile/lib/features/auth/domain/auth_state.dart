/// State representation for authentication and registration operations
sealed class AuthState {
  const AuthState();
}

class AuthInitial extends AuthState {
  const AuthInitial();
}

class AuthLoading extends AuthState {
  final String? message;
  const AuthLoading({this.message});
}

class AuthAuthenticated extends AuthState {
  final String accessToken;
  final String? refreshToken;
  final String? userId;
  final String? phone;

  const AuthAuthenticated({
    required this.accessToken,
    this.refreshToken,
    this.userId,
    this.phone,
  });
}

class AuthOtpPending extends AuthState {
  final String userId;
  final String phone;
  final DateTime? expiresAt;

  const AuthOtpPending({
    required this.userId,
    required this.phone,
    this.expiresAt,
  });
}

class AuthUnauthenticated extends AuthState {
  const AuthUnauthenticated();
}

class AuthError extends AuthState {
  final String message;
  final String? errorCode;
  final Map<String, dynamic>? fieldErrors;
  final bool isRetryable;

  const AuthError({
    required this.message,
    this.errorCode,
    this.fieldErrors,
    this.isRetryable = true,
  });
}
