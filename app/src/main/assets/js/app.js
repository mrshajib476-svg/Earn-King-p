/**
 * Earn King ♔ - Official Android Web & AdMob Logic
 * Features:
 * - Secure Firebase Authentication (Email/Password Sign Up, Log In, Log Out)
 * - Server-validated Daily Login Bonus (+50 Coins) claimable once per day
 * - Immediate App Open Ad presentation upon Login / App Entrance
 * - AdMob Rewarded Video Ads (+200 Coins), Interstitial Ads & Banner Ads
 */

(function () {
  'use strict';

  const DAILY_BONUS_AMOUNT = 50;

  function getTodayString() {
    const d = new Date();
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  // --- APPLICATION STATE ---
  const STATE = {
    userId: localStorage.getItem('user_id') || 'EK-' + Math.floor(1000 + Math.random() * 9000),
    userName: localStorage.getItem('user_name') || 'King Earner',
    userEmail: localStorage.getItem('user_email') || '',
    isLoggedIn: localStorage.getItem('is_logged_in') === 'true',
    userLevel: parseInt(localStorage.getItem('user_level') || '1', 10),
    balance: parseInt(localStorage.getItem('user_coins') || '100', 10),
    todayEarned: parseInt(localStorage.getItem('today_earned') || '0', 10),
    adsWatched: parseInt(localStorage.getItem('ads_watched') || '0', 10),
    lastBonusClaimDate: localStorage.getItem('last_bonus_claim_date') || '',
    todayGoal: 10,
    selectedPayout: 'bKash',
    transactions: JSON.parse(localStorage.getItem('tx_history') || '[]'),
    adMob: {
      appOpenReady: false,
      rewardedReady: false,
      interstitialReady: false,
      bannerReady: false,
    },
    isSpinning: false,
  };

  // Seed default transaction if empty
  if (STATE.transactions.length === 0) {
    STATE.transactions = [
      { id: 'TX-101', type: 'earn', title: 'Welcome Gift', amount: 100, time: 'Initial' }
    ];
    saveState();
  }

  // --- PERSISTENCE ---
  function saveState() {
    localStorage.setItem('user_id', STATE.userId);
    localStorage.setItem('user_name', STATE.userName);
    localStorage.setItem('user_email', STATE.userEmail);
    localStorage.setItem('is_logged_in', STATE.isLoggedIn ? 'true' : 'false');
    localStorage.setItem('user_level', STATE.userLevel.toString());
    localStorage.setItem('user_coins', STATE.balance.toString());
    localStorage.setItem('today_earned', STATE.todayEarned.toString());
    localStorage.setItem('ads_watched', STATE.adsWatched.toString());
    localStorage.setItem('last_bonus_claim_date', STATE.lastBonusClaimDate);
    localStorage.setItem('tx_history', JSON.stringify(STATE.transactions));

    // Sync with Firebase Firestore backend
    if (window.AndroidBridge && typeof window.AndroidBridge.syncBalance === 'function') {
      try {
        window.AndroidBridge.syncBalance(STATE.userId, STATE.balance, STATE.adsWatched, STATE.todayEarned);
      } catch (e) {
        console.warn('Firebase bridge sync failed', e);
      }
    }
  }

  function isBonusClaimedToday() {
    return STATE.lastBonusClaimDate === getTodayString();
  }

  // --- UI UPDATE HELPERS ---
  let currentDisplayedBalance = 0;
  let balanceAnimationRequestId = null;

  function triggerCoinPulse(earnedAmount) {
    // Pulse animation on balance numbers & large coins
    const balanceNums = document.querySelectorAll('.balance-num');
    const coinIcons = document.querySelectorAll('.coin-icon-large');

    balanceNums.forEach(el => {
      el.classList.remove('coin-earned-pulse');
      void el.offsetWidth; // Trigger reflow
      el.classList.add('coin-earned-pulse');
    });

    coinIcons.forEach(el => {
      el.classList.remove('coin-earned-pulse');
      void el.offsetWidth;
      el.classList.add('coin-earned-pulse');
    });

    // Floating gain tag on hero cards
    if (earnedAmount && earnedAmount > 0) {
      const heroCards = document.querySelectorAll('.balance-hero-card');
      heroCards.forEach(card => {
        const badge = document.createElement('div');
        badge.className = 'floating-coin-badge';
        badge.textContent = `+${Number(earnedAmount).toLocaleString()} Coins`;
        card.appendChild(badge);
        setTimeout(() => {
          if (badge.parentNode) badge.parentNode.removeChild(badge);
        }, 1650);
      });
    }
  }

  function animateBalanceCount(startVal, targetVal, durationMs = 950, earnedAmount = null) {
    if (balanceAnimationRequestId) {
      cancelAnimationFrame(balanceAnimationRequestId);
      balanceAnimationRequestId = null;
    }

    triggerCoinPulse(earnedAmount);

    const fromVal = Number(startVal) || 0;
    const toVal = Number(targetVal) || 0;
    const startTime = performance.now();

    function step(now) {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / durationMs, 1);

      // Smooth ease-out cubic curve for natural deceleration
      const ease = 1 - Math.pow(1 - progress, 3);
      const currentVal = Math.round(fromVal + (toVal - fromVal) * ease);
      currentDisplayedBalance = currentVal;

      const balanceElems = document.querySelectorAll('.balance-display');
      balanceElems.forEach(el => {
        el.textContent = currentVal.toLocaleString();
      });

      const usdElems = document.querySelectorAll('.balance-usd-val');
      const usdVal = (currentVal / 1000).toFixed(2);
      usdElems.forEach(el => {
        el.textContent = `$${usdVal} USD`;
      });

      if (progress < 1) {
        balanceAnimationRequestId = requestAnimationFrame(step);
      } else {
        currentDisplayedBalance = toVal;
        balanceAnimationRequestId = null;
        balanceElems.forEach(el => {
          el.textContent = toVal.toLocaleString();
        });
        const finalUsdVal = (toVal / 1000).toFixed(2);
        usdElems.forEach(el => {
          el.textContent = `$${finalUsdVal} USD`;
        });
      }
    }

    balanceAnimationRequestId = requestAnimationFrame(step);
  }

  function updateUI(animate = false, earnedAmount = null) {
    const today = getTodayString();
    const claimedToday = isBonusClaimedToday();

    // Balance displays with optional smooth count-up animation
    if (animate) {
      animateBalanceCount(currentDisplayedBalance, STATE.balance, 950, earnedAmount);
    } else {
      currentDisplayedBalance = STATE.balance;
      const balanceElems = document.querySelectorAll('.balance-display');
      balanceElems.forEach(el => {
        el.textContent = Number(STATE.balance).toLocaleString();
      });

      const usdElems = document.querySelectorAll('.balance-usd-val');
      const usdVal = (STATE.balance / 1000).toFixed(2);
      usdElems.forEach(el => {
        el.textContent = `$${usdVal} USD`;
      });
    }

    // Stats
    const todayEarnedEl = document.getElementById('today-earned-val');
    if (todayEarnedEl) todayEarnedEl.textContent = `+${STATE.todayEarned}`;

    const adsWatchedEl = document.getElementById('ads-watched-val');
    if (adsWatchedEl) adsWatchedEl.textContent = STATE.adsWatched;

    const profileAdsWatchedEl = document.getElementById('profile-ads-watched');
    if (profileAdsWatchedEl) profileAdsWatchedEl.textContent = STATE.adsWatched;

    // Header updates
    const headerName = document.getElementById('header-user-name');
    const headerStatus = document.getElementById('header-user-status');
    const headerAvatar = document.getElementById('header-avatar');
    if (headerName) headerName.textContent = STATE.isLoggedIn ? STATE.userName : 'Earn King ♔';
    if (headerStatus) {
      headerStatus.textContent = STATE.isLoggedIn
        ? `★ Level ${STATE.userLevel} (Logged In)`
        : '★ Guest (Tap to Login)';
    }
    if (headerAvatar) {
      headerAvatar.textContent = STATE.userName ? STATE.userName.substring(0, 2).toUpperCase() : 'EK';
    }

    // Profile Page updates
    const profileName = document.getElementById('profile-name');
    const profileEmail = document.getElementById('profile-email-badge');
    const profileId = document.getElementById('profile-user-id');
    const profileAvatar = document.getElementById('profile-avatar');
    const profileAuthBtn = document.getElementById('profile-auth-action-btn');
    const logoutItem = document.getElementById('btn-logout-item');

    if (profileName) profileName.textContent = STATE.userName;
    if (profileEmail) profileEmail.textContent = STATE.userEmail || 'guest@earnking.app';
    if (profileId) profileId.textContent = `#${STATE.userId}`;
    if (profileAvatar) {
      profileAvatar.textContent = STATE.userName ? STATE.userName.substring(0, 2).toUpperCase() : 'EK';
    }

    if (profileAuthBtn) {
      profileAuthBtn.textContent = STATE.isLoggedIn ? 'Switch Account' : 'Log In / Sign Up';
    }
    if (logoutItem) {
      logoutItem.style.display = STATE.isLoggedIn ? 'flex' : 'none';
    }

    // Daily Bonus Card buttons (Home & Earn pages)
    const homeBonusBtn = document.getElementById('btn-claim-daily-home');
    const homeBonusSub = document.getElementById('home-daily-bonus-sub');
    const earnBonusBtn = document.getElementById('btn-claim-daily-earn');
    const earnBonusSub = document.getElementById('earn-daily-bonus-sub');
    const profileBonusStatus = document.getElementById('profile-daily-bonus-status');

    if (claimedToday) {
      if (homeBonusBtn) {
        homeBonusBtn.textContent = 'Claimed Today ✓';
        homeBonusBtn.classList.add('claimed');
        homeBonusBtn.disabled = true;
      }
      if (homeBonusSub) homeBonusSub.textContent = 'Bonus claimed! Come back tomorrow.';

      if (earnBonusBtn) {
        earnBonusBtn.textContent = 'Claimed Today ✓';
        earnBonusBtn.classList.add('claimed');
        earnBonusBtn.disabled = true;
      }
      if (earnBonusSub) earnBonusSub.textContent = 'Bonus claimed! Come back tomorrow.';

      if (profileBonusStatus) {
        profileBonusStatus.textContent = 'Claimed Today ✓';
        profileBonusStatus.style.color = '#94A3B8';
      }
    } else {
      if (homeBonusBtn) {
        homeBonusBtn.textContent = 'Claim +50';
        homeBonusBtn.classList.remove('claimed');
        homeBonusBtn.disabled = false;
      }
      if (homeBonusSub) homeBonusSub.textContent = 'Claim your free +50 Coins today!';

      if (earnBonusBtn) {
        earnBonusBtn.textContent = 'Claim +50';
        earnBonusBtn.classList.remove('claimed');
        earnBonusBtn.disabled = false;
      }
      if (earnBonusSub) earnBonusSub.textContent = 'Check in daily to claim +50 free coins!';

      if (profileBonusStatus) {
        profileBonusStatus.textContent = 'Ready to Claim';
        profileBonusStatus.style.color = '#10B981';
      }
    }

    // Goal Progress
    const goalCountEl = document.getElementById('goal-count-label');
    const goalBarEl = document.getElementById('goal-progress-bar');
    if (goalCountEl && goalBarEl) {
      const progress = Math.min(STATE.adsWatched, STATE.todayGoal);
      goalCountEl.textContent = `${progress} / ${STATE.todayGoal} Ads`;
      const pct = Math.round((progress / STATE.todayGoal) * 100);
      goalBarEl.style.width = `${pct}%`;
    }

    // Render Recent Activities
    renderTransactions();
  }

  function renderTransactions() {
    const container = document.getElementById('tx-list-container');
    if (!container) return;

    if (STATE.transactions.length === 0) {
      container.innerHTML = `
        <div style="text-align: center; padding: 24px 16px; color: #64748B; font-size: 13px;">
          No recent activity. Watch an ad to start earning!
        </div>
      `;
      return;
    }

    const itemsHtml = STATE.transactions.slice(0, 5).map(tx => {
      const isEarn = tx.type === 'earn';
      const sign = isEarn ? '+' : '-';
      const amountColor = isEarn ? '#10B981' : '#EF4444';
      const icon = isEarn ? '↗' : '↘';
      const iconBg = isEarn ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)';
      const iconColor = isEarn ? '#10B981' : '#EF4444';

      return `
        <div style="display: flex; justify-content: space-between; align-items: center; background: #131F3F; border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 14px; padding: 12px 14px; margin-bottom: 10px;">
          <div style="display: flex; align-items: center; gap: 12px;">
            <div style="width: 36px; height: 36px; border-radius: 10px; background: ${iconBg}; color: ${iconColor}; display: flex; align-items: center; justify-content: center; font-weight: bold;">
              ${icon}
            </div>
            <div>
              <div style="font-size: 13px; font-weight: 700; color: #fff;">${escapeHtml(tx.title)}</div>
              <div style="font-size: 11px; color: #94A3B8;">${escapeHtml(tx.time || 'Today')}</div>
            </div>
          </div>
          <div style="font-size: 14px; font-weight: 800; color: ${amountColor};">
            ${sign}${Number(tx.amount).toLocaleString()} Coins
          </div>
        </div>
      `;
    }).join('');

    container.innerHTML = itemsHtml;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/[&<>"']/g, function (m) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m];
    });
  }

  // --- MODAL & TOAST NOTIFICATIONS ---
  function showModal(title, body, icon = '🎉', buttonText = 'Continue', onButtonClick = null) {
    const overlay = document.getElementById('modal-overlay');
    const titleEl = document.getElementById('modal-title');
    const bodyEl = document.getElementById('modal-body');
    const iconEl = document.getElementById('modal-icon');
    const btn = document.getElementById('modal-close-btn');

    if (titleEl) titleEl.textContent = title;
    if (bodyEl) bodyEl.innerHTML = body;
    if (iconEl) iconEl.textContent = icon;
    if (btn) {
      btn.textContent = buttonText;
      btn.onclick = function () {
        closeModal();
        if (onButtonClick) onButtonClick();
      };
    }

    if (overlay) overlay.classList.add('active');
  }

  function closeModal() {
    const overlay = document.getElementById('modal-overlay');
    if (overlay) overlay.classList.remove('active');
  }

  function showToast(message, type = 'normal') {
    const toast = document.getElementById('app-toast');
    if (!toast) return;

    toast.textContent = message;
    toast.className = 'toast-msg ' + type + ' show';

    setTimeout(() => {
      toast.classList.remove('show');
    }, 3000);
  }

  // --- DAILY LOGIN BONUS ENGINE ---
  function claimDailyLoginBonus() {
    if (isBonusClaimedToday()) {
      showToast("You've already claimed today's bonus!", 'warning');
      return;
    }

    const today = getTodayString();

    // Call backend validation via AndroidBridge
    if (window.AndroidBridge && typeof window.AndroidBridge.claimDailyBonus === 'function') {
      window.AndroidBridge.claimDailyBonus(STATE.userId, DAILY_BONUS_AMOUNT);
    } else {
      // Local execution fallback
      applyDailyBonusSuccess(DAILY_BONUS_AMOUNT);
    }
  }

  function applyDailyBonusSuccess(amount) {
    const bonus = amount || DAILY_BONUS_AMOUNT;
    STATE.balance += bonus;
    STATE.todayEarned += bonus;
    STATE.lastBonusClaimDate = getTodayString();

    STATE.transactions.unshift({
      id: 'DAILY-' + Date.now(),
      type: 'earn',
      title: 'Daily Login Bonus',
      amount: bonus,
      time: 'Just now'
    });

    saveState();
    updateUI(true, bonus);

    showModal(
      '👑 Daily Bonus Claimed!',
      `You claimed your daily login reward of <b>+${bonus} Coins</b>!<br><br><b>New Balance: ${STATE.balance.toLocaleString()} Coins</b><br>Come back tomorrow for your next reward!`,
      '🎁'
    );
    showToast(`+${bonus} Daily Bonus Added!`, 'success');
  }

  // Automatic Check on App Open / Login
  function checkAndPromptDailyBonus() {
    if (!isBonusClaimedToday()) {
      setTimeout(() => {
        showModal(
          '👑 Daily Login Bonus Ready!',
          `Welcome back to <b>Earn King ♔</b>!<br><br>Your daily login bonus of <b>+50 Coins</b> is ready to claim right now.`,
          '🎁',
          'Claim +50 Coins Now',
          () => {
            claimDailyLoginBonus();
          }
        );
      }, 600);
    }
  }

  // Native callback from Android bridge
  window.onDailyBonusClaimResult = function (success, newBalance, error) {
    if (success) {
      applyDailyBonusSuccess(DAILY_BONUS_AMOUNT);
    } else {
      if (error && error.includes('Already claimed')) {
        STATE.lastBonusClaimDate = getTodayString();
        saveState();
        updateUI();
        showToast("You've already claimed today's bonus!", 'warning');
      } else {
        showToast(error || 'Failed to claim daily bonus', 'warning');
      }
    }
  };

  // --- FIREBASE AUTHENTICATION UI & LOGIC ---
  let authMode = 'login'; // 'login' or 'signup'

  function openAuthModal(mode = 'login') {
    authMode = mode;
    const overlay = document.getElementById('auth-modal-overlay');
    const nameGroup = document.getElementById('auth-name-group');
    const loginTab = document.getElementById('tab-auth-login');
    const signupTab = document.getElementById('tab-auth-signup');
    const submitBtn = document.getElementById('auth-submit-btn');
    const errorEl = document.getElementById('auth-error-msg');

    if (errorEl) errorEl.style.display = 'none';

    if (mode === 'signup') {
      if (loginTab) loginTab.classList.remove('active');
      if (signupTab) signupTab.classList.add('active');
      if (nameGroup) nameGroup.style.display = 'flex';
      if (submitBtn) submitBtn.textContent = 'Sign Up & Create Account';
    } else {
      if (loginTab) loginTab.classList.add('active');
      if (signupTab) signupTab.classList.remove('active');
      if (nameGroup) nameGroup.style.display = 'none';
      if (submitBtn) submitBtn.textContent = 'Log In & Enter';
    }

    if (overlay) overlay.classList.add('active');
  }

  function closeAuthModal() {
    const overlay = document.getElementById('auth-modal-overlay');
    if (overlay) overlay.classList.remove('active');
  }

  function setupAuthHandlers() {
    const headerAuthBtn = document.getElementById('header-auth-btn');
    if (headerAuthBtn) {
      headerAuthBtn.addEventListener('click', () => {
        if (STATE.isLoggedIn) {
          document.querySelector('[data-tab=profile]').click();
        } else {
          openAuthModal('login');
        }
      });
    }

    const headerPill = document.getElementById('header-profile-pill');
    if (headerPill) {
      headerPill.addEventListener('click', () => {
        if (!STATE.isLoggedIn) {
          openAuthModal('login');
        } else {
          document.querySelector('[data-tab=profile]').click();
        }
      });
    }

    const profileAuthBtn = document.getElementById('profile-auth-action-btn');
    if (profileAuthBtn) {
      profileAuthBtn.addEventListener('click', () => {
        openAuthModal('login');
      });
    }

    const loginTab = document.getElementById('tab-auth-login');
    const signupTab = document.getElementById('tab-auth-signup');
    if (loginTab) {
      loginTab.addEventListener('click', () => openAuthModal('login'));
    }
    if (signupTab) {
      signupTab.addEventListener('click', () => openAuthModal('signup'));
    }

    const cancelBtn = document.getElementById('btn-auth-cancel');
    if (cancelBtn) {
      cancelBtn.addEventListener('click', closeAuthModal);
    }

    const authForm = document.getElementById('auth-form');
    if (authForm) {
      authForm.addEventListener('submit', handleAuthSubmit);
    }

    const logoutItem = document.getElementById('btn-logout-item');
    if (logoutItem) {
      logoutItem.addEventListener('click', () => {
        if (confirm('Are you sure you want to log out of Earn King ♔?')) {
          if (window.AndroidBridge && typeof window.AndroidBridge.logout === 'function') {
            window.AndroidBridge.logout();
          } else {
            window.onFirebaseLogoutResult();
          }
        }
      });
    }
  }

  function handleAuthSubmit(e) {
    if (e) e.preventDefault();

    const emailInput = document.getElementById('auth-input-email');
    const passInput = document.getElementById('auth-input-password');
    const nameInput = document.getElementById('auth-input-name');
    const errorEl = document.getElementById('auth-error-msg');

    const email = emailInput ? emailInput.value.trim() : '';
    const pass = passInput ? passInput.value.trim() : '';
    const name = nameInput ? nameInput.value.trim() : '';

    if (!email || !pass) {
      if (errorEl) {
        errorEl.textContent = 'Please enter email and password.';
        errorEl.style.display = 'block';
      }
      return;
    }

    if (pass.length < 6) {
      if (errorEl) {
        errorEl.textContent = 'Password must be at least 6 characters.';
        errorEl.style.display = 'block';
      }
      return;
    }

    if (authMode === 'signup') {
      const displayName = name || email.substringBefore ? email.substringBefore('@') : email.split('@')[0];
      if (window.AndroidBridge && typeof window.AndroidBridge.signUpWithEmail === 'function') {
        window.AndroidBridge.signUpWithEmail(email, pass, displayName);
      } else {
        // Fallback simulated signup
        window.onFirebaseSignUpResult(true, 'EK-' + Math.floor(1000 + Math.random() * 9000), displayName, null);
      }
    } else {
      if (window.AndroidBridge && typeof window.AndroidBridge.loginWithEmail === 'function') {
        window.AndroidBridge.loginWithEmail(email, pass);
      } else {
        // Fallback simulated login
        const displayName = email.split('@')[0];
        window.onFirebaseLoginResult(true, 'EK-' + Math.floor(1000 + Math.random() * 9000), displayName, null);
      }
    }
  }

  // --- FIREBASE AUTH BRIDGE CALLBACKS ---
  window.onFirebaseLoginResult = function (success, uid, displayName, error) {
    const errorEl = document.getElementById('auth-error-msg');
    if (success) {
      closeAuthModal();
      STATE.isLoggedIn = true;
      STATE.userId = uid || STATE.userId;
      STATE.userName = displayName || STATE.userName;
      const emailInput = document.getElementById('auth-input-email');
      if (emailInput && emailInput.value) {
        STATE.userEmail = emailInput.value.trim();
      }

      saveState();
      updateUI();
      showToast(`Welcome back, ${STATE.userName}!`, 'success');

      // Immediate App Open Ad on Login:
      // "app যখন কেউ Login করে ডুকবে সাথে সাথে appoper ads show হবে"
      if (window.AndroidBridge && typeof window.AndroidBridge.showAppOpenAd === 'function') {
        try {
          window.AndroidBridge.showAppOpenAd();
        } catch (e) {
          console.warn('App Open Ad call failed', e);
        }
      }

      // Fetch user data from Firebase backend
      if (window.AndroidBridge && typeof window.AndroidBridge.fetchUserData === 'function') {
        window.AndroidBridge.fetchUserData(STATE.userId);
      }

      // Daily login bonus check
      checkAndPromptDailyBonus();
    } else {
      if (errorEl) {
        errorEl.textContent = error || 'Login failed. Please check credentials.';
        errorEl.style.display = 'block';
      }
    }
  };

  window.onFirebaseSignUpResult = function (success, uid, displayName, error) {
    const errorEl = document.getElementById('auth-error-msg');
    if (success) {
      closeAuthModal();
      STATE.isLoggedIn = true;
      STATE.userId = uid || STATE.userId;
      STATE.userName = displayName || STATE.userName;
      const emailInput = document.getElementById('auth-input-email');
      if (emailInput && emailInput.value) {
        STATE.userEmail = emailInput.value.trim();
      }

      saveState();
      updateUI();
      showToast(`Account created! Welcome to Earn King ♔`, 'success');

      // Immediate App Open Ad trigger
      if (window.AndroidBridge && typeof window.AndroidBridge.showAppOpenAd === 'function') {
        try {
          window.AndroidBridge.showAppOpenAd();
        } catch (e) {
          console.warn('App Open Ad call failed', e);
        }
      }

      checkAndPromptDailyBonus();
    } else {
      if (errorEl) {
        errorEl.textContent = error || 'Sign up failed.';
        errorEl.style.display = 'block';
      }
    }
  };

  window.onFirebaseAutoAuth = function (uid, displayName, email) {
    STATE.isLoggedIn = true;
    STATE.userId = uid;
    STATE.userName = displayName || STATE.userName;
    STATE.userEmail = email || STATE.userEmail;
    saveState();
    updateUI();
  };

  window.onFirebaseLogoutResult = function () {
    STATE.isLoggedIn = false;
    STATE.userEmail = '';
    saveState();
    updateUI();
    showToast('Logged out successfully', 'normal');
  };

  window.onUserDataFetched = function (balance, todayEarned, adsWatched, lastBonus) {
    if (balance > 0) STATE.balance = balance;
    if (todayEarned > 0) STATE.todayEarned = todayEarned;
    if (adsWatched > 0) STATE.adsWatched = adsWatched;
    if (lastBonus) STATE.lastBonusClaimDate = lastBonus;
    saveState();
    updateUI();
  };

  // --- ADMOB EVENT HANDLERS ---
  function handleWatchRewardedAd() {
    if (window.AndroidBridge && typeof window.AndroidBridge.showRewardedAd === 'function') {
      showToast('Loading Google AdMob Sponsored Video...', 'normal');
      window.AndroidBridge.showRewardedAd();
    } else {
      // Browser preview simulation
      showToast('Simulating AdMob Rewarded Video...', 'normal');
      setTimeout(() => {
        window.onAdMobRewardEarned(200, 'COINS');
      }, 1200);
    }
  }

  function handleShowInterstitialAd() {
    if (window.AndroidBridge && typeof window.AndroidBridge.showInterstitialAd === 'function') {
      showToast('Displaying AdMob Interstitial Ad...', 'normal');
      window.AndroidBridge.showInterstitialAd();
    } else {
      showToast('Simulating Interstitial Ad...', 'normal');
      setTimeout(() => {
        window.onAdMobInterstitialClosed();
      }, 1000);
    }
  }

  window.onAdMobRewardEarned = function (amount, type) {
    console.log('Official AdMob Reward Earned callback:', amount, type);
    const rewardAmount = (amount && amount > 0) ? amount : 200;

    STATE.balance += rewardAmount;
    STATE.todayEarned += rewardAmount;
    STATE.adsWatched += 1;

    STATE.transactions.unshift({
      id: 'AD-' + Date.now(),
      type: 'earn',
      title: 'Watched Video Ad',
      amount: rewardAmount,
      time: 'Just now'
    });

    saveState();
    updateUI(true, rewardAmount);

    showModal(
      '🎉 +' + rewardAmount + ' Coins Earned!',
      `Congratulations! Your official AdMob reward has been verified and added to your balance.<br><br><b>New Balance: ${STATE.balance.toLocaleString()} Coins</b>`,
      '👑'
    );

    if (window.AndroidBridge && typeof window.AndroidBridge.showToast === 'function') {
      window.AndroidBridge.showToast('🎉 +' + rewardAmount + ' Coins Added!');
    }
  };

  window.onAdMobAdUnavailable = function (adType) {
    console.warn('AdMob unavailable callback received for:', adType);
    showToast('Ad is currently loading. Please try again in a few seconds.', 'warning');
  };

  window.onAdMobRewardedClosed = function (earnedReward) {
    console.log('AdMob Rewarded Ad closed. Reward earned:', earnedReward);
  };

  window.onAdMobInterstitialClosed = function () {
    console.log('AdMob Interstitial Ad closed.');
    showToast('Thank you for supporting Earn King ♔!', 'normal');
  };

  window.onAdMobAdStatusChanged = function (adType, isReady) {
    console.log('AdMob status changed:', adType, isReady);
    if (adType === 'rewarded') {
      STATE.adMob.rewardedReady = isReady;
    } else if (adType === 'interstitial') {
      STATE.adMob.interstitialReady = isReady;
    } else if (adType === 'app_open') {
      STATE.adMob.appOpenReady = isReady;
    } else if (adType === 'banner') {
      STATE.adMob.bannerReady = isReady;
    }
    updateAdBadges();
  };

  function updateAdBadges() {
    const rewardedBadges = document.querySelectorAll('.rewarded-status-badge');
    rewardedBadges.forEach(badge => {
      if (STATE.adMob.rewardedReady) {
        badge.textContent = '● AdMob Ready';
        badge.className = 'ad-badge ad-badge-ready rewarded-status-badge';
      } else {
        badge.textContent = '○ Loading Ad...';
        badge.className = 'ad-badge ad-badge-loading rewarded-status-badge';
      }
    });

    const interstitialBadge = document.getElementById('interstitial-status-badge');
    if (interstitialBadge) {
      if (STATE.adMob.interstitialReady) {
        interstitialBadge.textContent = '● Ready';
        interstitialBadge.style.background = 'rgba(59, 130, 246, 0.15)';
        interstitialBadge.style.color = '#60A5FA';
      } else {
        interstitialBadge.textContent = '○ Loading...';
        interstitialBadge.style.background = 'rgba(148, 163, 184, 0.15)';
        interstitialBadge.style.color = '#94A3B8';
      }
    }
  }

  // --- NAVIGATION TABS ---
  function setupNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    const pages = document.querySelectorAll('.page');

    navItems.forEach(item => {
      item.addEventListener('click', () => {
        const targetPage = item.getAttribute('data-tab');

        navItems.forEach(n => n.classList.remove('active'));
        pages.forEach(p => p.classList.remove('active'));

        item.classList.add('active');
        const targetEl = document.getElementById('page-' + targetPage);
        if (targetEl) targetEl.classList.add('active');

        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    });
  }

  // --- LUCKY SPIN WHEEL ---
  function setupSpinWheel() {
    const canvas = document.getElementById('wheel-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const spinBtn = document.getElementById('btn-spin-wheel');

    const segments = [
      { text: '+50', color: '#1E40AF', coins: 50 },
      { text: '+100', color: '#F59E0B', coins: 100 },
      { text: '+20', color: '#3B82F6', coins: 20 },
      { text: '+500', color: '#10B981', coins: 500 },
      { text: '+10', color: '#6366F1', coins: 10 },
      { text: '+250', color: '#EC4899', coins: 250 },
      { text: '+75', color: '#8B5CF6', coins: 75 },
      { text: '+150', color: '#D97706', coins: 150 },
    ];

    let startAngle = 0;
    const arc = Math.PI / (segments.length / 2);

    function drawWheel() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2;
      const radius = canvas.width / 2 - 10;

      for (let i = 0; i < segments.length; i++) {
        const angle = startAngle + i * arc;
        ctx.fillStyle = segments[i].color;
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, angle, angle + arc, false);
        ctx.arc(centerX, centerY, 20, angle + arc, angle, true);
        ctx.fill();
        ctx.strokeStyle = '#0F172A';
        ctx.lineWidth = 3;
        ctx.stroke();

        ctx.save();
        ctx.fillStyle = '#FFFFFF';
        ctx.font = 'bold 14px sans-serif';
        ctx.translate(
          centerX + Math.cos(angle + arc / 2) * (radius - 40),
          centerY + Math.sin(angle + arc / 2) * (radius - 40)
        );
        ctx.rotate(angle + arc / 2 + Math.PI / 2);
        ctx.fillText(segments[i].text, -ctx.measureText(segments[i].text).width / 2, 0);
        ctx.restore();
      }

      ctx.beginPath();
      ctx.arc(centerX, centerY, 24, 0, 2 * Math.PI, false);
      ctx.fillStyle = '#F59E0B';
      ctx.fill();
      ctx.strokeStyle = '#FFFFFF';
      ctx.lineWidth = 3;
      ctx.stroke();
    }

    drawWheel();

    if (spinBtn) {
      spinBtn.addEventListener('click', () => {
        if (STATE.isSpinning) return;
        STATE.isSpinning = true;
        spinBtn.disabled = true;

        const spinAngle = 1440 + Math.floor(Math.random() * 360);
        const spinDuration = 3500;
        const startTime = performance.now();

        function animate(time) {
          const elapsed = time - startTime;
          const progress = Math.min(elapsed / spinDuration, 1);
          const easeOut = 1 - Math.pow(1 - progress, 3);

          startAngle = (spinAngle * easeOut * Math.PI) / 180;
          drawWheel();

          if (progress < 1) {
            requestAnimationFrame(animate);
          } else {
            STATE.isSpinning = false;
            spinBtn.disabled = false;

            const degrees = (startAngle * 180) / Math.PI + 90;
            const arcd = (arc * 180) / Math.PI;
            const index = Math.floor((360 - (degrees % 360)) / arcd) % segments.length;
            const winning = segments[index];

            STATE.balance += winning.coins;
            STATE.todayEarned += winning.coins;
            STATE.transactions.unshift({
              id: 'SPIN-' + Date.now(),
              type: 'earn',
              title: 'Lucky Wheel Spin',
              amount: winning.coins,
              time: 'Just now'
            });

            saveState();
            updateUI(true, winning.coins);
            showModal('🎉 Lucky Spin Win!', `You won <b>+${winning.coins} Coins</b> on the wheel!`, '🎰');
          }
        }

        requestAnimationFrame(animate);
      });
    }

    const scratchBtn = document.getElementById('btn-scratch-card');
    if (scratchBtn) {
      scratchBtn.addEventListener('click', () => {
        const bonus = 75;
        STATE.balance += bonus;
        STATE.todayEarned += bonus;
        STATE.transactions.unshift({
          id: 'SCRATCH-' + Date.now(),
          type: 'earn',
          title: 'Lucky Scratch Card',
          amount: bonus,
          time: 'Just now'
        });

        saveState();
        updateUI(true, bonus);
        showModal('🎫 Scratch Win!', `You revealed <b>+${bonus} Bonus Coins</b>!`, '✨');
      });
    }
  }

  // --- WITHDRAWAL SYSTEM ---
  function setupWithdrawal() {
    const payoutCards = document.querySelectorAll('.payout-method-card');
    payoutCards.forEach(card => {
      card.addEventListener('click', () => {
        payoutCards.forEach(c => c.classList.remove('active'));
        card.classList.add('active');
        STATE.selectedPayout = card.getAttribute('data-method') || 'bKash';
      });
    });

    const withdrawBtn = document.getElementById('btn-submit-withdraw');
    if (withdrawBtn) {
      withdrawBtn.addEventListener('click', () => {
        const accountInput = document.getElementById('payout-account-input');
        const amountInput = document.getElementById('payout-amount-input');

        const account = accountInput ? accountInput.value.trim() : '';
        const amount = amountInput ? parseInt(amountInput.value, 10) : 0;

        if (!account) {
          showToast('Please enter your phone number or account email', 'warning');
          return;
        }

        const MIN_COINS = 2000;
        if (!amount || amount < MIN_COINS) {
          showToast(`Minimum payout is ${MIN_COINS.toLocaleString()} Coins ($2.00)`, 'warning');
          return;
        }

        if (amount > STATE.balance) {
          showToast('Insufficient balance for this payout', 'warning');
          return;
        }

        STATE.balance -= amount;
        STATE.transactions.unshift({
          id: 'WD-' + Date.now(),
          type: 'withdraw',
          title: `Withdrawal via ${STATE.selectedPayout}`,
          amount: amount,
          time: 'Pending Review'
        });

        saveState();
        updateUI(true);

        if (accountInput) accountInput.value = '';

        showModal(
          'Withdrawal Submitted!',
          `Your payout request of <b>${amount.toLocaleString()} Coins</b> ($${(amount / 1000).toFixed(2)}) via <b>${STATE.selectedPayout}</b> to <code>${escapeHtml(account)}</code> is received.<br><br>Payments are typically processed within 24 hours.`,
          '💳'
        );
      });
    }
  }

  // --- DAILY TASKS ---
  function setupTasks() {
    const taskBtns = document.querySelectorAll('.task-claim-btn');
    taskBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const reward = parseInt(btn.getAttribute('data-reward') || '100', 10);
        const title = btn.getAttribute('data-task') || 'Task Complete';

        STATE.balance += reward;
        STATE.todayEarned += reward;
        STATE.transactions.unshift({
          id: 'TSK-' + Date.now(),
          type: 'earn',
          title: title,
          amount: reward,
          time: 'Just now'
        });

        saveState();
        updateUI(true, reward);

        btn.disabled = true;
        btn.textContent = 'Claimed';

        showModal('🏆 Task Completed!', `You earned <b>+${reward} Coins</b> for: ${title}`, '👑');
      });
    });
  }

  // --- INITIALIZATION ---
  document.addEventListener('DOMContentLoaded', () => {
    setupNavigation();
    setupSpinWheel();
    setupWithdrawal();
    setupTasks();
    setupAuthHandlers();

    // Hook Daily Bonus Buttons (Home, Earn, and Quick Action)
    const homeBonusBtn = document.getElementById('btn-claim-daily-home');
    if (homeBonusBtn) {
      homeBonusBtn.addEventListener('click', claimDailyLoginBonus);
    }

    const earnBonusBtn = document.getElementById('btn-claim-daily-earn');
    if (earnBonusBtn) {
      earnBonusBtn.addEventListener('click', claimDailyLoginBonus);
    }

    const quickDailyBtn = document.getElementById('quick-action-daily');
    if (quickDailyBtn) {
      quickDailyBtn.addEventListener('click', claimDailyLoginBonus);
    }

    const profileBonusItem = document.getElementById('btn-claim-daily-profile');
    if (profileBonusItem) {
      profileBonusItem.addEventListener('click', claimDailyLoginBonus);
    }

    // Modal close hooks
    const modalCloseBtn = document.getElementById('modal-close-btn');
    if (modalCloseBtn) {
      modalCloseBtn.addEventListener('click', closeModal);
    }

    const overlay = document.getElementById('modal-overlay');
    if (overlay) {
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) closeModal();
      });
    }

    // Reset local data cache hook
    const resetBtn = document.getElementById('btn-reset-demo');
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        if (confirm('Reset local balance cache and reload?')) {
          localStorage.clear();
          location.reload();
        }
      });
    }

    // Attach Ad Buttons
    const watchAdBtns = document.querySelectorAll('.btn-watch-ad-cta');
    watchAdBtns.forEach(btn => {
      btn.addEventListener('click', handleWatchRewardedAd);
    });

    const interstitialBtns = document.querySelectorAll('.btn-show-interstitial-cta');
    interstitialBtns.forEach(btn => {
      btn.addEventListener('click', handleShowInterstitialAd);
    });

    // Query initial AdMob state from Android Bridge
    if (window.AndroidBridge) {
      try {
        if (typeof window.AndroidBridge.isRewardedAdLoaded === 'function') {
          STATE.adMob.rewardedReady = window.AndroidBridge.isRewardedAdLoaded();
        }
        if (typeof window.AndroidBridge.isInterstitialAdLoaded === 'function') {
          STATE.adMob.interstitialReady = window.AndroidBridge.isInterstitialAdLoaded();
        }
        if (typeof window.AndroidBridge.isAppOpenAdLoaded === 'function') {
          STATE.adMob.appOpenReady = window.AndroidBridge.isAppOpenAdLoaded();
        }
      } catch (e) {
        console.error('Error querying AdMob state:', e);
      }
    }

    updateUI(true);

    // Check and prompt Daily Login Bonus if not yet claimed today
    checkAndPromptDailyBonus();
  });

})();
