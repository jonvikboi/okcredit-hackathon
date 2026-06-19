<script>
  /** @type {any} */
  let { form } = $props();

  let loading = $state(false);
  let username = $state('');
  let password = $state('');

  $effect(() => {
    if (form?.username !== undefined) {
      username = form.username;
    }
  });

  function handleSubmit() {
    loading = true;
  }
</script>

<svelte:head>
  <title>Admin Login | Sunrise Fine Jewells</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@400;600;700&family=Inter:wght@400;500;600;700&family=Outfit:wght@400;500;600;700&family=Material+Symbols+Outlined" rel="stylesheet">
</svelte:head>

<div class="login-container">
  <div class="bg-blob bg-blob-1"></div>
  <div class="bg-blob bg-blob-2"></div>

  <div class="login-card">
    <div class="brand-logo">Sunrise</div>
    <div class="brand-subtitle">Fine Jewells</div>
    
    <div class="login-header-text">
      <h2>Merchant Portal</h2>
      <p>Enter your administrator credentials to access stock inventory and valuations.</p>
    </div>

    {#if form?.error}
      <div class="error-box">
        <span class="material-symbols-outlined">error</span>
        <span>{form.error}</span>
      </div>
    {/if}

    <form method="POST" action="?/login" onsubmit={handleSubmit} class="login-form">
      <div class="input-wrapper">
        <label class="input-label" for="username">Username</label>
        <input 
          id="username"
          name="username"
          type="text" 
          class="login-input" 
          placeholder="Enter username" 
          bind:value={username} 
          required 
          autocomplete="username"
        />
      </div>

      <div class="input-wrapper">
        <label class="input-label" for="password">Password</label>
        <input 
          id="password"
          name="password"
          type="password" 
          class="login-input" 
          placeholder="Enter password" 
          bind:value={password}
          required 
          autocomplete="current-password"
        />
      </div>

      <button type="submit" class="btn-login" disabled={loading}>
        {loading ? 'Authenticating...' : 'Sign In'}
      </button>
    </form>



    <div class="login-footer">
      Sunrise Fine Jewells &copy; 2026. Authorized Personnel Only.
    </div>
  </div>
</div>

<style>
  .login-container {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: radial-gradient(circle at center, #231d18 0%, #0c0a08 100%);
    position: relative;
    overflow: hidden;
    padding: 24px;
    font-family: 'Inter', sans-serif;
    color: #e8e2d8;
  }

  .bg-blob {
    position: absolute;
    width: 500px;
    height: 500px;
    background: radial-gradient(circle, rgba(154, 123, 62, 0.12) 0%, transparent 70%);
    filter: blur(60px);
    border-radius: 50%;
    pointer-events: none;
  }
  .bg-blob-1 {
    top: -150px;
    left: -150px;
  }
  .bg-blob-2 {
    bottom: -150px;
    right: -150px;
  }

  .login-card {
    width: 100%;
    max-width: 420px;
    background: rgba(35, 29, 24, 0.65);
    border: 1px solid rgba(154, 123, 62, 0.25);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border-radius: 12px;
    padding: 48px 40px;
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.65);
    text-align: center;
    z-index: 10;
    box-sizing: border-box;
    animation: scaleUp 0.45s cubic-bezier(0.16, 1, 0.3, 1);
  }

  @keyframes scaleUp {
    from { transform: scale(0.96); opacity: 0; }
    to { transform: scale(1); opacity: 1; }
  }

  .brand-logo {
    font-family: 'Cormorant Garamond', serif;
    font-size: 38px;
    font-weight: 700;
    color: #9a7b3e;
    letter-spacing: 0.06em;
    margin-bottom: 2px;
    text-transform: uppercase;
    line-height: 1;
  }

  .brand-subtitle {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.28em;
    color: rgba(215, 196, 164, 0.65);
    margin-bottom: 32px;
  }

  .login-header-text {
    margin-bottom: 32px;
  }

  .login-header-text h2 {
    font-family: 'Outfit', sans-serif;
    font-size: 20px;
    font-weight: 500;
    margin: 0 0 8px;
    color: #f4f1ec;
  }

  .login-header-text p {
    font-size: 13px;
    color: rgba(215, 196, 164, 0.6);
    line-height: 1.5;
    margin: 0;
  }

  .error-box {
    background: rgba(185, 28, 28, 0.1);
    color: #fca5a5;
    border: 1px solid rgba(185, 28, 28, 0.25);
    padding: 12px 16px;
    border-radius: 6px;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 24px;
    text-align: left;
  }

  .error-box .material-symbols-outlined {
    font-size: 18px;
    color: #ef4444;
  }

  .login-form {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .input-wrapper {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }

  .input-label {
    font-size: 10px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: rgba(215, 196, 164, 0.85);
  }

  .login-input {
    width: 100%;
    background: rgba(18, 14, 11, 0.6);
    border: 1.5px solid rgba(154, 123, 62, 0.25);
    color: #f4f1ec;
    padding: 12px 16px;
    font-size: 14px;
    border-radius: 6px;
    transition: all 0.25s ease;
    box-sizing: border-box;
  }

  .login-input:focus {
    outline: none;
    border-color: #9a7b3e;
    box-shadow: 0 0 0 3px rgba(154, 123, 62, 0.15);
  }

  .btn-login {
    width: 100%;
    padding: 14px;
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.15em;
    background: linear-gradient(135deg, #9a7b3e, #c4a35a);
    color: #ffffff;
    border: none;
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.25s ease;
    box-shadow: 0 4px 15px rgba(154, 123, 62, 0.25);
    margin-top: 8px;
  }

  .btn-login:hover {
    background: linear-gradient(135deg, #c4a35a, #9a7b3e);
    box-shadow: 0 6px 20px rgba(154, 123, 62, 0.35);
    transform: translateY(-1px);
  }

  .btn-login:active {
    transform: translateY(0);
  }

  .btn-login:disabled {
    background: rgba(154, 123, 62, 0.4);
    box-shadow: none;
    cursor: not-allowed;
    transform: none;
  }

  .login-footer {
    margin-top: 36px;
    font-size: 10px;
    color: rgba(215, 196, 164, 0.4);
    letter-spacing: 0.05em;
  }


</style>
