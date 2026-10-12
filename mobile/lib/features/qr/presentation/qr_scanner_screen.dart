import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/qr_service.dart';
import '../domain/qr_models.dart';

class QrScannerScreen extends ConsumerStatefulWidget {
  final Function(ResolvedQrModel resolved)? onResolved;

  const QrScannerScreen({super.key, this.onResolved});

  @override
  ConsumerState<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends ConsumerState<QrScannerScreen> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.normal,
    facing: CameraFacing.back,
  );
  bool _isProcessing = false;
  String? _errorMessage;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _handleBarcode(String rawValue) async {
    if (_isProcessing) return;
    HapticFeedback.lightImpact();
    setState(() {
      _isProcessing = true;
      _errorMessage = null;
    });

    try {
      final resolved = await ref.read(qrServiceProvider).resolveReference(rawValue);

      if (resolved.isExpired) {
        if (mounted) {
          setState(() {
            _isProcessing = false;
            _errorMessage = 'This QR code has expired. Please ask recipient for a new code.';
          });
        }
        return;
      }

      if (resolved.isUsed) {
        if (mounted) {
          setState(() {
            _isProcessing = false;
            _errorMessage = 'This one-time QR code has already been used.';
          });
        }
        return;
      }

      if (mounted) {
        if (widget.onResolved != null) {
          widget.onResolved!(resolved);
        } else {
          Navigator.of(context).pop(resolved);
        }
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isProcessing = false;
          _errorMessage = 'Unable to resolve QR reference. Ensure it is a valid LankaPay QR code.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          MobileScanner(
            controller: _controller,
            onDetect: (capture) {
              final barcodes = capture.barcodes;
              for (final barcode in barcodes) {
                final raw = barcode.rawValue;
                if (raw != null && raw.isNotEmpty) {
                  _handleBarcode(raw);
                  break;
                }
              }
            },
          ),

          // Top navigation bar overlay
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(20),
                    child: BackdropFilter(
                      filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                      child: Container(
                        color: Colors.black.withOpacity(0.4),
                        child: IconButton(
                          icon: const Icon(Icons.arrow_back_ios_new_rounded, color: Colors.white, size: 20),
                          tooltip: 'Back',
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                      ),
                    ),
                  ),
                  Row(
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(20),
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                          child: Container(
                            color: Colors.black.withOpacity(0.4),
                            child: IconButton(
                              icon: const Icon(Icons.flash_on_rounded, color: Colors.white),
                              tooltip: 'Toggle Flash',
                              onPressed: () {
                                HapticFeedback.lightImpact();
                                _controller.toggleTorch();
                              },
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(20),
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                          child: Container(
                            color: Colors.black.withOpacity(0.4),
                            child: IconButton(
                              icon: const Icon(Icons.flip_camera_ios_rounded, color: Colors.white),
                              tooltip: 'Switch Camera',
                              onPressed: () {
                                HapticFeedback.lightImpact();
                                _controller.switchCamera();
                              },
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Scanner Overlay Frame
          Center(
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(
                border: Border.all(color: AppTokens.primaryViolet, width: 3),
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(
                    color: AppTokens.primaryViolet.withOpacity(0.3),
                    blurRadius: 24,
                    spreadRadius: 2,
                  ),
                ],
              ),
            ),
          ),

          // Feedback banner at bottom
          Positioned(
            bottom: 40,
            left: 24,
            right: 24,
            child: Column(
              children: [
                if (_errorMessage != null)
                  Container(
                    margin: const EdgeInsets.only(bottom: 16),
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: AppTokens.error,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: Text(
                      _errorMessage!,
                      style: const TextStyle(
                        fontFamily: AppTokens.fontFamily,
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                if (_isProcessing)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.75),
                      borderRadius: BorderRadius.circular(30),
                      border: Border.all(color: Colors.white24),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(color: AppTokens.lightViolet, strokeWidth: 2),
                        ),
                        SizedBox(width: 12),
                        Text(
                          'Resolving reference...',
                          style: TextStyle(
                            fontFamily: AppTokens.fontFamily,
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
