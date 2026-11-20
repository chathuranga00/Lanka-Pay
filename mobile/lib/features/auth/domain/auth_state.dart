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
  final String? fullName;

  const AuthAuthenticated({
    required this.accessToken,
    this.refreshToken,
    this.userId,
    this.phone,
    this.fullName,
  });
}

class AuthOtpPending extends AuthState {
  final String userId;
  final String phone;
  final DateTime? expiresAt;
  final String? fullName;

  const AuthOtpPending({
    required this.userId,
    required this.phone,
    this.expiresAt,
    this.fullName,
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
